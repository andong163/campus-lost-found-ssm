package com.team.cms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cms.auth.AuthConstants;
import com.team.cms.auth.service.AuthService;
import com.team.cms.common.web.RequestLogFilter;
import com.team.cms.config.SpringConfig;
import com.team.cms.config.SpringMvcConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.CharacterEncodingFilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 郭金滔 GJT R4 验收测试：身份权限矩阵回归。
 *
 * <p>逐个核对公开、学生、审核员、只读和管理员的接口权限与资源所有权，并覆盖
 * 未登录、无权限、越权、停用和撤权五类边界。与「只验证登录成功」不同，本类对每个角色
 * 都同时断言「该放行的放行」和「该拦截的拦截」，并验证授权变化对既有会话立即生效。</p>
 *
 * <p>矩阵依据 {@code sql/migrations/003-auth-permission-seed.sql} 与测试夹具
 * {@code src/test/resources/auth-schema-test.sql} 中的角色-权限映射；未被授权的规划权限码
 * （{@code PermissionCodes} 中的 41 个）按团队约定不自动授权，因此学生和只读用户在这些
 * 业务接口上返回 403，由 {@code IdentityContractAuditTest} 与迁移草案统一跟踪。</p>
 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextHierarchy({@ContextConfiguration(name = "root", classes = SpringConfig.class),
    @ContextConfiguration(name = "mvc", classes = SpringMvcConfig.class)})
@TestPropertySource(properties = {"jdbc.driver=org.h2.Driver",
    "jdbc.url=jdbc:h2:mem:permmatrix;MODE=MySQL;DB_CLOSE_DELAY=-1", "jdbc.username=sa", "jdbc.password="})
@Sql({"/review-claim-schema.sql", "/auth-schema-test.sql"})
public class PermissionMatrixTest {
    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "admin12345";
    private static final String USER_PASSWORD = "password123";

    /** 学生、审核员、只读都应被拦截的管理类接口。 */
    private static final String[] ADMIN_ONLY_ENDPOINTS = {
        "/api/users", "/api/roles", "/api/permissions", "/api/operation-logs",
        "/api/categories/tree", "/api/reviews"
    };

    @Autowired private WebApplicationContext context;
    @Autowired private AuthService authService;
    @Autowired private DataSource dataSource;

    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Before public void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(new CharacterEncodingFilter("UTF-8", true), new RequestLogFilter())
            .addDispatcherServletCustomizer(servlet -> servlet.setThrowExceptionIfNoHandlerFound(true))
            .build();
    }

    // ---------- 公开访问 ----------

    /** 未登录直接调用受保护接口，一律 401（不能因为绕过页面就放行）。 */
    @Test public void unauthenticatedCallsAreRejected() throws Exception {
        for (String url : new String[] {"/api/auth/me", "/api/users", "/api/roles", "/api/permissions",
                "/api/operation-logs", "/api/reviews", "/api/categories/tree", "/api/items/mine"}) {
            assertEquals("未登录访问 " + url + " 应返回 401", 401, status(null, get(url)));
        }
        assertEquals("未登录的写请求同样拒绝", 401, status(null, post("/api/categories")));
    }

    // ---------- 管理员 ----------

    /** 管理员是唯一拥有全部已授权权限码的角色，所有受检接口都应放行。 */
    @Test public void administratorReachesEveryCheckedEndpoint() throws Exception {
        MockHttpSession admin = adminSession();
        for (String url : new String[] {"/api/auth/me", "/api/users", "/api/roles", "/api/permissions",
                "/api/operation-logs", "/api/reviews", "/api/categories/tree"}) {
            assertEquals("管理员访问 " + url + " 应返回 200", 200, status(admin, get(url)));
        }
    }

    /** 响应体不得包含密码或哈希字段，避免身份接口泄露凭据。 */
    @Test public void responsesNeverExposePasswordHash() throws Exception {
        MockHttpSession admin = adminSession();
        String body = body(mvc.perform(get("/api/users").session(admin)).andReturn());
        assertFalse("用户列表不得返回 password 相关字段", body.toLowerCase().contains("password"));
        assertTrue(body.contains("\"records\""));
    }

    // ---------- 学生 ----------

    /** 学生只拿到 EDITOR：能登录、能看自己，但不能进入管理类接口。 */
    @Test public void studentIsDeniedAdministrativeEndpoints() throws Exception {
        JsonNode student = register("student_a", "学生甲");
        MockHttpSession session = login("student_a", USER_PASSWORD);

        assertEquals(200, status(session, get("/api/auth/me")));
        for (String url : ADMIN_ONLY_ENDPOINTS) {
            assertEquals("学生访问 " + url + " 应返回 403", 403, status(session, get(url)));
        }
        // 请求体必须合法：参数校验先于权限切面，否则只会得到 400 而测不到 403。
        long selfId = student.get("id").asLong();
        assertEquals("学生不能替他人分配角色", 403, status(session, put("/api/users/" + selfId + "/roles")
            .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"ADMIN\"]}")));
        assertEquals("学生不能启停账号", 403, status(session, put("/api/users/" + selfId + "/status")
            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")));
    }

    // ---------- 审核员 ----------

    /** 审核员能进审核工作台，但进不了用户与角色管理，也看不到审计日志。 */
    @Test public void reviewerReachesReviewWorkbenchOnly() throws Exception {
        long id = register("reviewer_1", "审核员甲").get("id").asLong();
        MockHttpSession admin = adminSession();
        assertEquals(200, assignRoles(admin, id, "REVIEWER"));

        MockHttpSession reviewer = login("reviewer_1", USER_PASSWORD);
        assertEquals("审核员应能访问审核工作台", 200, status(reviewer, get("/api/reviews")));
        assertEquals(403, status(reviewer, get("/api/users")));
        assertEquals(403, status(reviewer, get("/api/roles")));
        assertEquals("审核员没有 audit:view", 403, status(reviewer, get("/api/operation-logs")));
    }

    // ---------- 只读 ----------

    /** 只读用户没有任何已授权权限码：可登录查看自身身份，其余一律 403。 */
    @Test public void viewerIsReadOnlyAndPermissionless() throws Exception {
        long id = register("viewer_1", "只读甲").get("id").asLong();
        MockHttpSession admin = adminSession();
        assertEquals(200, assignRoles(admin, id, "VIEWER"));

        MockHttpSession viewer = login("viewer_1", USER_PASSWORD);
        assertEquals(200, status(viewer, get("/api/auth/me")));
        for (String url : ADMIN_ONLY_ENDPOINTS) {
            assertEquals("只读用户访问 " + url + " 应返回 403", 403, status(viewer, get(url)));
        }
    }

    // ---------- 停用 ----------

    /** 停用账号后，旧会话立即失效，不能继续用缓存的登录态访问。 */
    @Test public void disablingAccountInvalidatesExistingSession() throws Exception {
        long id = register("student_b", "学生乙").get("id").asLong();
        MockHttpSession session = login("student_b", USER_PASSWORD);
        assertEquals(200, status(session, get("/api/auth/me")));

        MockHttpSession admin = adminSession();
        assertEquals(200, setEnabled(admin, id, false));

        assertEquals("停用后旧会话应返回 401", 401, status(session, get("/api/auth/me")));
        assertEquals("停用后旧会话访问业务接口同样 401", 401, status(session, get("/api/users")));
    }

    // ---------- 撤权 ----------

    /** 角色被撤回后，同一会话的下一次请求立即 403，不需要重新登录。 */
    @Test public void revokingRoleTakesEffectOnExistingSession() throws Exception {
        long id = register("reviewer_2", "审核员乙").get("id").asLong();
        MockHttpSession admin = adminSession();
        assertEquals(200, assignRoles(admin, id, "REVIEWER"));

        MockHttpSession reviewer = login("reviewer_2", USER_PASSWORD);
        assertEquals(200, status(reviewer, get("/api/reviews")));

        assertEquals(200, assignRoles(admin, id, "VIEWER"));
        assertEquals("撤权后同一会话应立即 403", 403, status(reviewer, get("/api/reviews")));
        assertEquals("降级后仍可确认身份", 200, status(reviewer, get("/api/auth/me")));
    }

    // ---------- 越权与所有权 ----------

    /** 资源所有权：非本人且非审核人员不能查看他人信息的审核记录。 */
    @Test public void foreignRecordsAreDeniedByOwnership() throws Exception {
        long studentId = register("student_c", "学生丙").get("id").asLong();
        long reviewerId = register("reviewer_3", "审核员丙").get("id").asLong();
        MockHttpSession admin = adminSession();
        assertEquals(200, assignRoles(admin, reviewerId, "REVIEWER"));

        long itemId = createDraftAsAdmin(admin);
        MockHttpSession owner = admin;
        MockHttpSession student = login("student_c", USER_PASSWORD);
        MockHttpSession reviewer = login("reviewer_3", USER_PASSWORD);

        assertEquals("作者本人可查看", 200, status(owner, get("/api/items/" + itemId + "/review-records")));
        assertEquals("审核人员可查看", 200, status(reviewer, get("/api/items/" + itemId + "/review-records")));
        assertEquals("非本人且非审核人员应 403", 403,
            status(student, get("/api/items/" + itemId + "/review-records")));
        assertNotEquals(0L, studentId);
    }

    // ---------- 学生本人范围 ----------

    /**
     * 004 迁移发布 item:* 之后，学生能用自己的信息接口，但仍然碰不到管理类接口。
     * <p>这条用例把「规划码未授权所以学生一律 403」的旧结论钉死为历史，防止文档与实现再次漂移。</p>
     */
    @Test public void studentReachesOwnItemEndpoints() throws Exception {
        register("student_d", "学生丁");
        MockHttpSession student = login("student_d", USER_PASSWORD);

        assertEquals("学生可查看本人信息列表", 200, status(student, get("/api/items/mine")));

        long categoryId = insertCategory();
        String payload = json.writeValueAsString(java.util.Map.of(
            "title", "校园卡遗失", "content", "在图书馆二楼遗失一张校园卡，请拾到者联系失主。",
            "itemType", "LOST", "categoryId", categoryId, "eventTime", "2026-09-01T10:00:00",
            "location", "图书馆二楼", "contactInfo", "13800000000"));
        MvcResult result = mvc.perform(post("/api/items").session(student)
            .header(AuthConstants.CSRF_HEADER, csrf(student))
            .contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn();
        assertEquals("学生可创建本人信息: " + body(result), 200, result.getResponse().getStatus());

        assertEquals("学生仍不能管理分类", 403, status(student, get("/api/categories/tree")));
        assertEquals("学生仍不能管理用户", 403, status(student, get("/api/users")));
    }

    // ---------- 最后一个管理员 ----------

    /** 管理员可管理性：不能停用或降级最后一个有效管理员。 */
    @Test public void lastAdministratorCannotBeDisabledOrDowngraded() throws Exception {
        MockHttpSession admin = adminSession();
        long adminId = json.readTree(body(mvc.perform(get("/api/auth/me").session(admin)).andReturn()))
            .path("data").path("id").asLong();

        assertEquals("不能停用最后一个管理员", 409, setEnabled(admin, adminId, false));
        assertEquals("不能移除最后一个管理员的角色", 409, assignRoles(admin, adminId, "VIEWER"));
        assertEquals("管理员本身仍可正常访问", 200, status(admin, get("/api/users")));
    }

    // ---------- 辅助方法 ----------

    private int status(MockHttpSession session, MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(session == null ? request : request.session(session)).andReturn();
        return result.getResponse().getStatus();
    }

    private String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private JsonNode register(String username, String displayName) throws Exception {
        String payload = json.writeValueAsString(java.util.Map.of(
            "username", username, "displayName", displayName, "password", USER_PASSWORD));
        MvcResult result = mvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn();
        assertEquals("注册失败: " + body(result), 200, result.getResponse().getStatus());
        return json.readTree(body(result)).get("data");
    }

    private MockHttpSession login(String username, String password) throws Exception {
        String payload = json.writeValueAsString(java.util.Map.of("username", username, "password", password));
        MvcResult result = mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn();
        assertEquals("登录失败: " + body(result), 200, result.getResponse().getStatus());
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession adminSession() throws Exception {
        authService.ensureAdmin(ADMIN, ADMIN_PASSWORD);
        return login(ADMIN, ADMIN_PASSWORD);
    }

    private String csrf(MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        assertEquals("应能取得写请求令牌: " + body(result), 200, result.getResponse().getStatus());
        return json.readTree(body(result)).get("data").asText();
    }

    private int assignRoles(MockHttpSession admin, long userId, String... roleCodes) throws Exception {
        String payload = json.writeValueAsString(java.util.Map.of("roleCodes", java.util.List.of(roleCodes)));
        return mvc.perform(put("/api/users/" + userId + "/roles").session(admin)
            .header(AuthConstants.CSRF_HEADER, csrf(admin))
            .contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn().getResponse().getStatus();
    }

    private int setEnabled(MockHttpSession admin, long userId, boolean enabled) throws Exception {
        String payload = json.writeValueAsString(java.util.Map.of("enabled", enabled));
        return mvc.perform(put("/api/users/" + userId + "/status").session(admin)
            .header(AuthConstants.CSRF_HEADER, csrf(admin))
            .contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn().getResponse().getStatus();
    }

    /** 管理员建一个分类与一条草稿，用于验证资源所有权。 */
    private long createDraftAsAdmin(MockHttpSession admin) throws Exception {
        long categoryId = insertCategory();
        String payload = json.writeValueAsString(java.util.Map.of(
            "title", "校园卡遗失", "content", "在图书馆二楼自习区遗失一张校园卡，请拾到者联系失主。",
            "itemType", "LOST", "categoryId", categoryId, "eventTime", "2026-09-01T10:00:00",
            "location", "图书馆二楼", "contactInfo", "13800000000"));
        MvcResult result = mvc.perform(post("/api/items").session(admin)
            .header(AuthConstants.CSRF_HEADER, csrf(admin))
            .contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn();
        assertEquals("建草稿失败: " + body(result), 200, result.getResponse().getStatus());
        return json.readTree(body(result)).path("data").path("id").asLong();
    }

    private long insertCategory() throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO cms_category(name, parent_id, sort_order, enabled, deleted, version) "
                        + "VALUES ('证件', NULL, 0, 1, 0, 0)", Statement.RETURN_GENERATED_KEYS)) {
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue("应能取回新分类主键", keys.next());
                return keys.getLong(1);
            }
        }
    }
}
