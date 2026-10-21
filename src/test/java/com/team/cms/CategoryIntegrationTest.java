package com.team.cms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cms.category.dto.CategoryCreateRequest;
import com.team.cms.category.service.CategoryAccessService;
import com.team.cms.category.service.CategoryService;
import com.team.cms.common.exception.BusinessException;
import com.team.cms.common.web.RequestLogFilter;
import com.team.cms.config.SpringConfig;
import com.team.cms.config.SpringMvcConfig;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** SR R1 分类模块集成测试：独立 H2 库 jdbc:h2:mem:category，不影响其他测试的 foundation 库。 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextHierarchy({@ContextConfiguration(name = "root", classes = SpringConfig.class),
    @ContextConfiguration(name = "mvc", classes = SpringMvcConfig.class)})
@TestPropertySource(properties = {"jdbc.driver=org.h2.Driver",
    "jdbc.url=jdbc:h2:mem:category;MODE=MySQL;DB_CLOSE_DELAY=-1", "jdbc.username=sa", "jdbc.password="})
@Sql("/schema-category.sql")
public class CategoryIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private CategoryService categoryService;
    @Autowired private CategoryAccessService categoryAccess;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Before public void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(new RequestLogFilter())
            .addDispatcherServletCustomizer(servlet -> servlet.setThrowExceptionIfNoHandlerFound(true)).build();
    }

    private JdbcTemplate sql() {
        return new JdbcTemplate(context.getBean(DataSource.class));
    }

    private JsonNode assertCode(MockHttpServletResponse response, int code) throws Exception {
        assertEquals(code, response.getStatus());
        // JSON 规范默认 UTF-8：按字节解析，避免 Mock 响应默认 ISO-8859-1 解码导致中文乱码。
        JsonNode body = json.readTree(response.getContentAsByteArray());
        assertEquals(code, body.get("code").asInt());
        assertTrue(body.has("message"));
        assertTrue(body.has("data"));
        assertNotNull(response.getHeader("X-Request-Id"));
        return body;
    }

    private MockHttpServletResponse postCategory(String name, Long parentId) throws Exception {
        return postCategoryRaw(name, parentId, null);
    }

    private MockHttpServletResponse postCategoryRaw(String name, Long parentId, Integer sortOrder) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("parentId", parentId);
        payload.put("sortOrder", sortOrder);
        return mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
            .characterEncoding(StandardCharsets.UTF_8.name())
            .content(json.writeValueAsString(payload))).andReturn().getResponse();
    }

    private long create(String name, Long parentId) throws Exception {
        return create(name, parentId, 0);
    }

    private long create(String name, Long parentId, int sortOrder) throws Exception {
        JsonNode body = assertCode(postCategoryRaw(name, parentId, sortOrder), 200);
        return body.get("data").get("id").asLong();
    }

    private void assertBusinessException(Runnable action, int status) {
        try {
            action.run();
            fail("应抛出 BusinessException " + status);
        } catch (BusinessException error) {
            assertEquals(status, error.getStatus().value());
        }
    }

    @Test public void createNestedTreeAndAdminTreeOrder() throws Exception {
        long electronics = create("电子产品", null);
        long ids = create("证件", null);
        long phone = create("手机", electronics, 5);
        create("电脑", electronics, 2);
        create("手机壳", phone, 0);

        JsonNode tree = assertCode(mvc.perform(get("/api/categories/tree")).andReturn().getResponse(), 200).get("data");
        assertEquals(2, tree.size());
        assertEquals("电子产品", tree.get(0).get("name").asText()); // 同级 sortOrder 相同，按 id 稳定排序
        assertEquals("证件", tree.get(1).get("name").asText());
        JsonNode children = tree.get(0).get("children");
        assertEquals(2, children.size());
        assertEquals("电脑", children.get(0).get("name").asText()); // sortOrder 2 在前
        assertEquals("手机", children.get(1).get("name").asText()); // sortOrder 5 在后
        assertEquals(1, children.get(1).get("children").size());
        assertEquals("手机壳", children.get(1).get("children").get(0).get("name").asText());
    }

    @Test public void rejectMissingOrDeletedParent() throws Exception {
        assertCode(postCategory("孤儿", 999L), 404);
        long parent = create("父", null);
        sql().update("UPDATE cms_category SET deleted=1 WHERE id=?", parent);
        assertCode(postCategory("孤儿", parent), 404);
    }

    @Test public void rejectDisabledParentOrAncestor() throws Exception {
        long root = create("根", null);
        long mid = create("中间", root);
        sql().update("UPDATE cms_category SET enabled=0 WHERE id=?", mid);
        assertCode(postCategory("叶子", mid), 409); // 直接父停用
        sql().update("UPDATE cms_category SET enabled=1 WHERE id=?", mid);
        sql().update("UPDATE cms_category SET enabled=0 WHERE id=?", root);
        assertCode(postCategory("叶子", mid), 409); // 祖先停用，直接父仍启用
    }

    @Test public void rejectDuplicateSiblingNameAllowDifferentParentAndRebuild() throws Exception {
        long phone = create("手机", null);
        assertCode(postCategory("手机", null), 409);   // 同名同级
        assertCode(postCategory(" 手机 ", null), 409); // trim 后同名
        long other = create("其他", null);
        assertCode(postCategory("手机", other), 200);  // 不同父级允许
        sql().update("UPDATE cms_category SET deleted=1 WHERE id=?", phone);
        assertCode(postCategory("手机", null), 200);   // 已删除后可重建
    }

    @Test public void siblingNameComparisonIsCaseInsensitiveInJava() throws Exception {
        create("Phone", null);
        assertCode(postCategory("phone", null), 409);
        assertCode(postCategory(" PHONE ", null), 409);
    }

    @Test public void publicTreeHidesDisabledNodeAndSubtree() throws Exception {
        long a = create("A", null);
        create("A1", a);
        create("A2", a);
        long b = create("B", null);
        long b1 = create("B1", b);

        sql().update("UPDATE cms_category SET enabled=0 WHERE id=?", b1);
        JsonNode tree = assertCode(mvc.perform(get("/api/public/categories")).andReturn().getResponse(), 200).get("data");
        assertEquals(2, tree.size());
        assertEquals("A", tree.get(0).get("name").asText());
        assertEquals("B", tree.get(1).get("name").asText());
        assertEquals(0, tree.get(1).get("children").size()); // 停用的 B1 消失

        sql().update("UPDATE cms_category SET enabled=0 WHERE id=?", a);
        tree = assertCode(mvc.perform(get("/api/public/categories")).andReturn().getResponse(), 200).get("data");
        assertEquals(1, tree.size());
        assertEquals("B", tree.get(0).get("name").asText()); // A 停用，A/A1/A2 整棵消失
    }

    @Test public void adminTreeKeepsDisabledNode() throws Exception {
        long a = create("A", null);
        long a1 = create("A1", a);
        sql().update("UPDATE cms_category SET enabled=0 WHERE id=?", a1);
        JsonNode tree = assertCode(mvc.perform(get("/api/categories/tree")).andReturn().getResponse(), 200).get("data");
        JsonNode node = tree.get(0).get("children").get(0);
        assertEquals("A1", node.get("name").asText());
        assertFalse(node.get("enabled").asBoolean()); // 管理树保留停用节点并标记
    }

    @Test public void requireBindableCoversExistenceEnabledAndAncestors() throws Exception {
        long root = create("根", null);
        long mid = create("中", root);
        long leaf = create("叶", mid);

        categoryAccess.requireBindable(leaf);
        assertTrue(categoryAccess.isBindable(leaf));
        assertFalse(categoryAccess.isBindable(9999L));
        assertFalse(categoryAccess.isBindable(null));

        assertBusinessException(() -> categoryAccess.requireBindable(9999L), 404);
        assertBusinessException(() -> categoryAccess.requireBindable(null), 400);
        sql().update("UPDATE cms_category SET enabled=0 WHERE id=?", mid);
        assertBusinessException(() -> categoryAccess.requireBindable(leaf), 409); // 祖先停用
        sql().update("UPDATE cms_category SET enabled=1 WHERE id=?", mid);
        sql().update("UPDATE cms_category SET deleted=1 WHERE id=?", mid);
        assertBusinessException(() -> categoryAccess.requireBindable(leaf), 404); // 父被逻辑删除，链断裂
    }

    @Test public void concurrentDuplicateSiblingOnlyOneSucceeds() throws Exception {
        long parent = create("并发父", null);
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    CategoryCreateRequest request = new CategoryCreateRequest();
                    request.setName("并发同名");
                    request.setParentId(parent);
                    try {
                        categoryService.create(request);
                        return true;
                    } catch (BusinessException error) {
                        assertEquals(409, error.getStatus().value());
                        return false;
                    }
                }));
            }
            ready.await();
            start.countDown();
            int success = 0;
            for (Future<Boolean> future : results) {
                if (future.get()) {
                    success++;
                }
            }
            assertEquals(1, success);
        } finally {
            pool.shutdown();
        }
    }

    @Test public void parameterValidationReturns400() throws Exception {
        assertCode(postCategory("", null), 400);
        assertCode(postCategory("   ", null), 400);
        assertCode(postCategory(String.join("", Collections.nCopies(81, "a")), null), 400);
        Map<String, Object> negative = new HashMap<>();
        negative.put("name", "x");
        negative.put("sortOrder", -1);
        assertCode(mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(negative))).andReturn().getResponse(), 400);
        assertCode(mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"x\",\"parentId\":0}")).andReturn().getResponse(), 400);
        assertCode(mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"x\",\"parentId\":1.9}")).andReturn().getResponse(), 400); // 小数 ID 拒绝
    }

    @Ignore("等待郭金滔 R1 的权限注解与拦截器合入后启用：未登录 401 / 无权限 403")
    @Test public void writeEndpointRequiresPermission() {
    }
}
