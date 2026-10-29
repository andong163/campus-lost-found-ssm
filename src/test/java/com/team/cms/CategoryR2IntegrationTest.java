package com.team.cms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cms.auth.service.AuthService;
import com.team.cms.category.service.CategoryReferenceGuard;
import com.team.cms.common.web.RequestLogFilter;
import com.team.cms.config.SpringConfig;
import com.team.cms.config.SpringMvcConfig;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * SR R2 分类管理集成测试：改、移、排序、启停、逻辑删除与关联限制。
 *
 * <p>独立 H2 库 jdbc:h2:mem:category-r2，与 R1 的 category 库互不影响。
 * "分类被内容引用"通过运行期注册一个测试用 {@link CategoryReferenceGuard} 模拟，
 * 这样不必让测试依赖袁运轩的物品表，也不用改生产代码。</p>
 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextHierarchy({@ContextConfiguration(name = "root", classes = SpringConfig.class),
    @ContextConfiguration(name = "mvc", classes = SpringMvcConfig.class)})
@TestPropertySource(properties = {"jdbc.driver=org.h2.Driver",
    "jdbc.url=jdbc:h2:mem:category-r2;MODE=MySQL;DB_CLOSE_DELAY=-1", "jdbc.username=sa", "jdbc.password="})
@Sql({"/auth-schema-test.sql", "/article-schema-test.sql", "/schema-category.sql"})
public class CategoryR2IntegrationTest {
    private static final String PASSWORD = "category-test-password";
    private static final String GUARD_BEAN = "testCategoryReferenceGuard";
    /** 被标记为"当前有内容引用"的分类 id；每个用例开始前清空。 */
    private static final Set<Long> REFERENCED = ConcurrentHashMap.newKeySet();

    @Autowired private WebApplicationContext context;
    @Autowired private AuthService authService;
    private MockMvc mvc;
    private MockHttpSession adminSession;
    private final ObjectMapper json = new ObjectMapper();

    @Before public void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(new RequestLogFilter())
            .addDispatcherServletCustomizer(servlet -> servlet.setThrowExceptionIfNoHandlerFound(true)).build();
        authService.ensureAdmin("r2admin", PASSWORD);
        adminSession = login("r2admin");
        REFERENCED.clear();
        // guard 必须注册到 root 上下文（CategoryServiceImpl 在 root，MVC 上下文的 Bean 对它不可见）。
        // 只注册一次：guard 的行为完全由静态 REFERENCED 驱动，复用同一实例即可。
        ConfigurableListableBeanFactory beanFactory =
            ((ConfigurableApplicationContext) context.getParent()).getBeanFactory();
        if (!beanFactory.containsSingleton(GUARD_BEAN)) {
            beanFactory.registerSingleton(GUARD_BEAN, new CategoryReferenceGuard() {
                @Override public String blockingReason(Long categoryId) {
                    return REFERENCED.contains(categoryId) ? "该分类下还有未删除的物品信息，不能删除" : null;
                }
            });
        }
    }

    // ---------------------------------------------------------------- 改

    @Test public void renameUpdatesNameAndReportsDuplicateSiblingAsConflict() throws Exception {
        long books = create("书籍", null);
        long material = create("教材", null);

        JsonNode renamed = assertCode(putJson("/api/categories/" + books, "{\"name\":\"图书\"}"), 200);
        assertEquals("图书", renamed.get("data").get("name").asText());

        // 同级重名：前后空格与大小写都应视为同一个名字
        assertCode(putJson("/api/categories/" + material, "{\"name\":\" 图书 \"}"), 409);
        assertCode(putJson("/api/categories/" + material, "{\"name\":\"图书\"}"), 409);

        // 不同父级允许同名
        long child = create("图书", books);
        assertEquals("图书", adminTreeName(child));

        // 参数校验
        assertCode(putJson("/api/categories/" + material, "{\"name\":\"\"}"), 400);
        assertCode(putJson("/api/categories/" + material, "{\"name\":\"" + "x".repeat(81) + "\"}"), 400);
        // 分类不存在
        assertCode(putJson("/api/categories/999999", "{\"name\":\"不存在\"}"), 404);
    }

    // ---------------------------------------------------------------- 移

    @Test public void moveRejectsSelfDescendantAndDisabledTarget() throws Exception {
        long root = create("根", null);
        long middle = create("中间", root);
        long leaf = create("叶子", middle);

        // 移到自己
        assertCode(moveCategory(root, root), 409);
        // 移到自己的后代（会成环）
        assertCode(moveCategory(root, leaf), 409);
        // 目标分类不存在
        assertCode(moveCategory(middle, 999999L), 404);
        // 目标分类已停用
        long disabled = create("停用目录", null);
        assertCode(toggleCategory(disabled, false), 200);
        assertCode(moveCategory(middle, disabled), 409);

        // 合法移动：叶子挂到根下，中间节点仍在根下
        assertCode(moveCategory(leaf, root), 200);
        JsonNode tree = adminTree();
        JsonNode rootNode = findTx(tree, "根");
        assertEquals(2, rootNode.get("children").size());
        assertEquals(1, countByName(rootNode.get("children"), "叶子"));

        // 合法移动：挂回根级
        assertCode(moveCategory(middle, null), 200);
    }

    // ---------------------------------------------------------------- 排序

    @Test public void sortUpdatesOrderAndValidatesRange() throws Exception {
        long a = create("排序A", null, 5);
        long b = create("排序B", null, 2);

        assertEquals("排序B", adminTree().get(0).get("name").asText());
        assertEquals("排序A", adminTree().get(1).get("name").asText());

        assertCode(sortCategory(a, 1), 200);
        assertEquals("排序A", adminTree().get(0).get("name").asText());
        assertEquals("排序B", adminTree().get(1).get("name").asText());

        // 负值拒绝
        assertCode(sortCategory(b, -1), 400);
        // 不存在的分类
        assertCode(sortCategory(999999L, 0), 404);
    }

    // ---------------------------------------------------------------- 启停

    @Test public void disablingNodeHidesWholeSubtreeFromPublicTree() throws Exception {
        long parent = create("启停父", null);
        create("启停子", parent);

        JsonNode pub = publicTree();
        assertEquals(1, pub.size());
        assertEquals("启停父", pub.get(0).get("name").asText());
        assertEquals(1, pub.get(0).get("children").size());

        assertCode(toggleCategory(parent, false), 200);
        // 父停用 → 整棵子树对公开接口隐藏
        assertEquals(0, publicTree().size());
        // 管理树仍可见，只是 enabled=false
        JsonNode admin = adminTree();
        assertEquals(1, admin.size());
        assertFalse(admin.get(0).get("enabled").asBoolean());

        assertCode(toggleCategory(parent, true), 200);
        assertEquals(1, publicTree().size());

        // 不存在的分类
        assertCode(toggleCategory(999999L, false), 404);
        // 缺少 enabled 字段
        assertCode(putJson("/api/categories/" + parent + "/enabled", "{}"), 400);
    }

    // ---------------------------------------------------------------- 删除

    @Test public void deleteRejectsChildrenAndReferencesThenLogicallyRemovesNode() throws Exception {
        long parent = create("删除父", null);
        long child = create("删除子", parent);

        // 有子分类 → 409
        assertCode(deleteCategory(parent), 409);
        // 被内容引用 → 409
        REFERENCED.add(child);
        assertCode(deleteCategory(child), 409);
        REFERENCED.remove(child);

        // 正常删除
        assertCode(deleteCategory(child), 200);
        // 已删除再删 → 404
        assertCode(deleteCategory(child), 404);

        JsonNode tree = adminTree();
        assertEquals(0, tree.get(0).get("children").size());
        // 逻辑删除而非物理删除
        assertEquals(Integer.valueOf(1), sql().queryForObject(
            "SELECT COUNT(*) FROM cms_category WHERE id = ? AND deleted = 1", Integer.class, child));
    }

    // ---------------------------------------------------------------- 权限与路由边界

    @Test public void managementEndpointsRequireLoginAndPermission() throws Exception {
        long id = create("权限校验", null);
        MockHttpSession student = registerStudent("r2student");

        assertCode(mvc.perform(put("/api/categories/" + id).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"未登录\"}")).andReturn().getResponse(), 401);
        assertCode(putJsonWith(student, "/api/categories/" + id, "{\"name\":\"无权限\"}"), 403);
        assertCode(moveCategoryWith(student, id, null), 403);
        assertCode(sortCategoryWith(student, id, 0), 403);
        assertCode(toggleCategoryWith(student, id, false), 403);
        assertCode(mvc.perform(delete("/api/categories/" + id).session(student)).andReturn().getResponse(), 403);

        // 公开分类树仍然匿名可读，且没有因为管理接口而放开其他路由
        assertCode(mvc.perform(get("/api/public/categories")).andReturn().getResponse(), 200);
        assertCode(mvc.perform(get("/api/categories/tree")).andReturn().getResponse(), 401);
    }

    // ---------------------------------------------------------------- helpers

    private MockHttpSession login(String username) throws Exception {
        org.springframework.test.web.servlet.MvcResult result = mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", username, "password", PASSWORD)))).andReturn();
        assertCode(result.getResponse(), 200);
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession registerStudent(String username) throws Exception {
        assertCode(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", username, "displayName", "R2 分类学生",
                "password", PASSWORD)))).andReturn().getResponse(), 200);
        return login(username);
    }

    private JsonNode assertCode(MockHttpServletResponse response, int code) throws Exception {
        assertEquals(code, response.getStatus());
        JsonNode body = json.readTree(response.getContentAsByteArray());
        assertEquals(code, body.get("code").asInt());
        assertTrue(body.has("message"));
        return body;
    }

    private JdbcTemplate sql() {
        return new JdbcTemplate(context.getBean(DataSource.class));
    }

    private long create(String name, Long parentId) throws Exception {
        return create(name, parentId, 0);
    }

    private long create(String name, Long parentId, int sortOrder) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("parentId", parentId);
        payload.put("sortOrder", sortOrder);
        JsonNode body = assertCode(mvc.perform(post("/api/categories").session(adminSession)
            .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8.name())
            .content(json.writeValueAsString(payload))).andReturn().getResponse(), 200);
        return body.get("data").get("id").asLong();
    }

    private MockHttpServletResponse putJson(String path, String content) throws Exception {
        return putJsonWith(adminSession, path, content);
    }

    private MockHttpServletResponse putJsonWith(MockHttpSession session, String path, String content) throws Exception {
        return mvc.perform(put(path).session(session).contentType(MediaType.APPLICATION_JSON)
            .characterEncoding(StandardCharsets.UTF_8.name()).content(content)).andReturn().getResponse();
    }

    private MockHttpServletResponse moveCategory(long id, Long parentId) throws Exception {
        return moveCategoryWith(adminSession, id, parentId);
    }

    private MockHttpServletResponse moveCategoryWith(MockHttpSession session, long id, Long parentId) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("parentId", parentId);
        return putJsonWith(session, "/api/categories/" + id + "/parent", json.writeValueAsString(payload));
    }

    private MockHttpServletResponse sortCategory(long id, long sortOrder) throws Exception {
        return sortCategoryWith(adminSession, id, sortOrder);
    }

    private MockHttpServletResponse sortCategoryWith(MockHttpSession session, long id, long sortOrder) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sortOrder", sortOrder);
        return putJsonWith(session, "/api/categories/" + id + "/sort", json.writeValueAsString(payload));
    }

    private MockHttpServletResponse toggleCategory(long id, boolean enabled) throws Exception {
        return toggleCategoryWith(adminSession, id, enabled);
    }

    private MockHttpServletResponse toggleCategoryWith(MockHttpSession session, long id, boolean enabled) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("enabled", enabled);
        return putJsonWith(session, "/api/categories/" + id + "/enabled", json.writeValueAsString(payload));
    }

    private MockHttpServletResponse deleteCategory(long id) throws Exception {
        return mvc.perform(delete("/api/categories/" + id).session(adminSession)).andReturn().getResponse();
    }

    private JsonNode adminTree() throws Exception {
        return assertCode(mvc.perform(get("/api/categories/tree").session(adminSession))
            .andReturn().getResponse(), 200).get("data");
    }

    private JsonNode publicTree() throws Exception {
        return assertCode(mvc.perform(get("/api/public/categories")).andReturn().getResponse(), 200).get("data");
    }

    private JsonNode findTx(JsonNode nodes, String name) {
        for (JsonNode node : nodes) {
            if (name.equals(node.get("name").asText())) {
                return node;
            }
        }
        throw new AssertionError("管理树中找不到分类：" + name);
    }

    private int countByName(JsonNode nodes, String name) {
        int count = 0;
        for (JsonNode node : nodes) {
            if (name.equals(node.get("name").asText())) {
                count++;
            }
        }
        return count;
    }

    private String adminTreeName(long id) throws Exception {
        for (JsonNode node : flatten(adminTree())) {
            if (node.get("id").asLong() == id) {
                return node.get("name").asText();
            }
        }
        throw new AssertionError("管理树中找不到 id：" + id);
    }

    private java.util.List<JsonNode> flatten(JsonNode nodes) {
        java.util.List<JsonNode> all = new java.util.ArrayList<>();
        for (JsonNode node : nodes) {
            all.add(node);
            JsonNode children = node.get("children");
            if (children != null) {
                all.addAll(flatten(children));
            }
        }
        return all;
    }
}
