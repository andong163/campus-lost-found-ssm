package com.team.cms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cms.auth.service.AuthService;
import com.team.cms.category.service.CategoryAccessService;
import com.team.cms.config.SpringConfig;
import com.team.cms.config.SpringMvcConfig;
import com.team.cms.support.SqlCounter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * SR R3 查询优化集成测试：批量可绑定判定、公开可见子树，以及它们相对逐条判定的 SQL 次数证据。
 *
 * <p>独立 H2 库 jdbc:h2:mem:category-r3。SQL 计数由 {@link SqlCounter} 在 MyBatis 层挂载，
 * 只统计真正执行的语句，不含连接池与上下文启动。</p>
 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextHierarchy({@ContextConfiguration(name = "root", classes = SpringConfig.class),
    @ContextConfiguration(name = "mvc", classes = SpringMvcConfig.class)})
@TestPropertySource(properties = {"jdbc.driver=org.h2.Driver",
    "jdbc.url=jdbc:h2:mem:category-r3;MODE=MySQL;DB_CLOSE_DELAY=-1", "jdbc.username=sa", "jdbc.password="})
@Sql({"/auth-schema-test.sql", "/article-schema-test.sql", "/schema-category.sql"})
public class CategoryR3IntegrationTest {
    private static final String PASSWORD = "category-r3-password";

    private static SqlCounter counter;

    @Autowired private WebApplicationContext context;
    @Autowired private AuthService authService;
    @Autowired private CategoryAccessService accessService;
    @Autowired private SqlSessionFactory sqlSessionFactory;
    private MockMvc mvc;
    private MockHttpSession adminSession;
    private final ObjectMapper json = new ObjectMapper();

    @Before public void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        authService.ensureAdmin("r3admin", PASSWORD);
        adminSession = login("r3admin");
        if (counter == null) {
            // 同一个 SqlSessionFactory 只挂一次，避免多个拦截器重复计数。
            counter = SqlCounter.attach(sqlSessionFactory.getConfiguration());
        }
        counter.reset();
    }

    // ---------------------------------------------------------------- 批量判定

    @Test public void batchBindableCheckCostsOneQueryInsteadOfOnePerItem() throws Exception {
        long root = create("图书", null);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ids.add(create("子类" + i, root));
        }

        counter.reset();
        for (Long id : ids) {
            assertTrue(accessService.isBindable(id));
        }
        int perItem = counter.count();

        counter.reset();
        Set<Long> bindable = accessService.bindableIds(ids);
        int batch = counter.count();

        assertEquals("20 个分类应全部可绑定", 20, bindable.size());
        assertEquals("逐条判定应发 20 次查询，实际 " + perItem, 20, perItem);
        assertEquals("批量判定应只发 1 次查询，实际 " + batch, 1, batch);
    }

    @Test public void bindableIdsAgreesWithSingleCheckForDisabledAncestorsAndMissingIds() throws Exception {
        long root = create("图书", null);
        long enabled = create("教材", root);
        long disabled = create("停用类", null);
        long underDisabled = create("停用类的子类", disabled);
        toggle(disabled, false);

        Set<Long> bindable = accessService.bindableIds(
            java.util.Arrays.asList(root, enabled, disabled, underDisabled, 999_999L));

        assertEquals(Set.of(root, enabled), bindable);
        // 与逐条判定保持一致，作为"批量方法与单条方法同语义"的证据。
        for (Long id : java.util.Arrays.asList(root, enabled, disabled, underDisabled, 999_999L)) {
            assertEquals("id " + id + " 的批量与单条结论必须一致", accessService.isBindable(id), bindable.contains(id));
        }
    }

    @Test public void bindableIdsToleratesNullAndEmptyInput() {
        assertTrue(accessService.bindableIds(null).isEmpty());
        assertTrue(accessService.bindableIds(List.of()).isEmpty());
        assertTrue(accessService.bindableIds(java.util.Arrays.asList((Long) null, 999_999L)).isEmpty());
    }

    // ---------------------------------------------------------------- 公开可见子树

    @Test public void publicSubtreeSkipsDisabledBranchEntirely() throws Exception {
        long root = create("图书", null);
        long enabled = create("教材", root);
        long enabledChild = create("高数", enabled);
        long disabled = create("停用分店", root);
        long underDisabled = create("停用分店下的子类", disabled);
        toggle(disabled, false);

        List<Long> visible = accessService.publicSubtreeIds(root);

        assertEquals(Set.of(root, enabled, enabledChild), new HashSet<>(visible));
        assertTrue("停用节点自身不可见", !visible.contains(disabled));
        assertTrue("停用节点的后代整棵隐藏", !visible.contains(underDisabled));
    }

    @Test public void publicSubtreeReturnsEmptyForMissingDisabledOrAnonymousRoot() throws Exception {
        long disabledRoot = create("停用根", null);
        toggle(disabledRoot, false);

        assertTrue(accessService.publicSubtreeIds(null).isEmpty());
        assertTrue(accessService.publicSubtreeIds(999_999L).isEmpty());
        assertTrue("停用子树根应返回空", accessService.publicSubtreeIds(disabledRoot).isEmpty());
    }

    @Test public void publicSubtreeCostsOneQueryRegardlessOfDepth() throws Exception {
        long root = create("图书", null);
        long level1 = create("教材", root);
        long level2 = create("高数", level1);
        create("上册", level2);

        counter.reset();
        List<Long> ids = accessService.publicSubtreeIds(root);
        assertEquals(4, ids.size());
        assertEquals("子树检索应只发 1 次查询，实际 " + counter.count(), 1, counter.count());
    }

    // ---------------------------------------------------------------- helpers

    private MockHttpSession login(String username) throws Exception {
        org.springframework.test.web.servlet.MvcResult result = mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
            .andReturn();
        assertEquals(200, result.getResponse().getStatus());
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private long create(String name, Long parentId) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("parentId", parentId);
        payload.put("sortOrder", 0);
        JsonNode body = assertCode(mvc.perform(post("/api/categories").session(adminSession)
            .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8.name())
            .content(json.writeValueAsString(payload))).andReturn().getResponse(), 200);
        return body.get("data").get("id").asLong();
    }

    private void toggle(long id, boolean enabled) throws Exception {
        assertCode(mvc.perform(put("/api/categories/" + id + "/enabled").session(adminSession)
            .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8.name())
            .content(json.writeValueAsString(Map.of("enabled", enabled)))).andReturn().getResponse(), 200);
    }

    private JsonNode assertCode(MockHttpServletResponse response, int code) throws Exception {
        assertEquals(code, response.getStatus());
        JsonNode body = json.readTree(response.getContentAsByteArray());
        assertEquals(code, body.get("code").asInt());
        return body;
    }
}
