package com.team.cms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cms.auth.service.AuthService;
import com.team.cms.common.web.RequestLogFilter;
import com.team.cms.config.SpringConfig;
import com.team.cms.config.SpringMvcConfig;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.CharacterEncodingFilter;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * 郭金滔 GJT R2 验收测试：用户分页与启停、角色分配、权限维护，
 * 以及「停用/改角色对既有会话立即生效」「最后一个管理员保护」「响应不含密码哈希」。
 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextHierarchy({@ContextConfiguration(name = "root", classes = SpringConfig.class),
    @ContextConfiguration(name = "mvc", classes = SpringMvcConfig.class)})
@TestPropertySource(properties = {"jdbc.driver=org.h2.Driver",
    "jdbc.url=jdbc:h2:mem:useradmin;MODE=MySQL;DB_CLOSE_DELAY=-1", "jdbc.username=sa", "jdbc.password="})
@Sql("/auth-schema-test.sql")
public class UserRoleAdminTest {
    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "admin12345";
    private static final String USER_PASSWORD = "password123";

    @Autowired private WebApplicationContext context;
    @Autowired private AuthService authService;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Before public void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(new CharacterEncodingFilter("UTF-8", true), new RequestLogFilter())
            .addDispatcherServletCustomizer(servlet -> servlet.setThrowExceptionIfNoHandlerFound(true)).build();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode register(String username, String displayName) throws Exception {
        String payload = json.writeValueAsString(java.util.Map.of(
            "username", username, "displayName", displayName, "password", USER_PASSWORD));
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(payload)).andReturn();
        assertEquals("注册应成功: " + result.getResponse().getContentAsString(),
            200, result.getResponse().getStatus());
        return body(result).get("data");
    }

    private MockHttpSession login(String username, String password) throws Exception {
        String payload = json.writeValueAsString(java.util.Map.of("username", username, "password", password));
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(payload)).andReturn();
        assertEquals("登录应成功: " + result.getResponse().getContentAsString(),
            200, result.getResponse().getStatus());
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession adminSession() throws Exception {
        authService.ensureAdmin(ADMIN, ADMIN_PASSWORD);
        return login(ADMIN, ADMIN_PASSWORD);
    }

    @Test public void studentCannotManageUsersOrRoles() throws Exception {
        register("stu10", "学生甲");
        MockHttpSession session = login("stu10", USER_PASSWORD);
        assertEquals(403, mvc.perform(get("/api/users").session(session)).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(get("/api/roles").session(session)).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(get("/api/permissions").session(session)).andReturn()
            .getResponse().getStatus());
        assertEquals(403, mvc.perform(put("/api/users/1/roles").session(session)
            .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"ADMIN\"]}"))
            .andReturn().getResponse().getStatus());
    }

    @Test public void adminPagesUsersWithoutPasswordFields() throws Exception {
        register("stu11", "学生乙");
        MockHttpSession admin = adminSession();
        MvcResult result = mvc.perform(get("/api/users").param("page", "1").param("size", "10")
            .session(admin)).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        String text = result.getResponse().getContentAsString();
        assertFalse("响应不得包含密码哈希字段: " + text,
            text.contains("passwordHash") || text.contains("password_hash"));
        JsonNode data = body(result).get("data");
        assertTrue("应至少包含管理员与学生两个账号", data.get("total").asLong() >= 2);
        assertTrue("列表应包含 stu11: " + data, data.get("records").toString().contains("stu11"));

        MvcResult filtered = mvc.perform(get("/api/users").param("roleCode", "EDITOR").session(admin))
            .andReturn();
        assertEquals(200, filtered.getResponse().getStatus());
        String filteredText = filtered.getResponse().getContentAsString();
        assertTrue("按角色过滤应命中 stu11: " + filteredText, filteredText.contains("stu11"));
        assertFalse("按 EDITOR 过滤不应命中管理员: " + filteredText, filteredText.contains("\"admin\""));
    }

    @Test public void disablingUserRejectsExistingSessionImmediately() throws Exception {
        JsonNode student = register("stu12", "学生丙");
        long studentId = student.get("id").asLong();
        MockHttpSession studentSession = login("stu12", USER_PASSWORD);
        assertEquals(200, mvc.perform(get("/api/auth/me").session(studentSession)).andReturn()
            .getResponse().getStatus());

        MockHttpSession admin = adminSession();
        assertEquals(200, mvc.perform(put("/api/users/" + studentId + "/status").session(admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
            .andReturn().getResponse().getStatus());

        assertEquals("停用后旧会话应立即失效", 401, mvc.perform(get("/api/auth/me").session(studentSession))
            .andReturn().getResponse().getStatus());
        assertEquals("停用账号也不能再登录", 401, mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(java.util.Map.of(
                "username", "stu12", "password", USER_PASSWORD))))
            .andReturn().getResponse().getStatus());
    }

    @Test public void roleChangeTakesEffectInExistingSession() throws Exception {
        JsonNode student = register("stu13", "学生丁");
        long studentId = student.get("id").asLong();
        MockHttpSession session = login("stu13", USER_PASSWORD);
        assertEquals("学生默认无 user:manage", 403,
            mvc.perform(get("/api/auth-probe/admin-only").session(session)).andReturn()
                .getResponse().getStatus());

        MockHttpSession admin = adminSession();
        assertEquals(200, mvc.perform(put("/api/users/" + studentId + "/roles").session(admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"ADMIN\"]}"))
            .andReturn().getResponse().getStatus());
        assertEquals("授予 ADMIN 后同一会话应立即通过", 200,
            mvc.perform(get("/api/auth-probe/admin-only").session(session)).andReturn()
                .getResponse().getStatus());

        assertEquals(200, mvc.perform(put("/api/users/" + studentId + "/roles").session(admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"EDITOR\"]}"))
            .andReturn().getResponse().getStatus());
        assertEquals("撤销后同一会话应立即被拒绝", 403,
            mvc.perform(get("/api/auth-probe/admin-only").session(session)).andReturn()
                .getResponse().getStatus());
    }

    @Test public void cannotDisableOrDemoteLastEnabledAdmin() throws Exception {
        MockHttpSession admin = adminSession();
        long adminId = body(mvc.perform(get("/api/auth/me").session(admin)).andReturn())
            .get("data").get("id").asLong();

        assertEquals(409, mvc.perform(put("/api/users/" + adminId + "/status").session(admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
            .andReturn().getResponse().getStatus());
        assertEquals(409, mvc.perform(put("/api/users/" + adminId + "/roles").session(admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"EDITOR\"]}"))
            .andReturn().getResponse().getStatus());
    }

    @Test public void adminMaintainsRolesAndPermissions() throws Exception {
        MockHttpSession admin = adminSession();

        MvcResult roles = mvc.perform(get("/api/roles").session(admin)).andReturn();
        assertEquals(200, roles.getResponse().getStatus());
        assertTrue(body(roles).get("data").toString().contains("ADMIN"));

        MvcResult permissions = mvc.perform(get("/api/permissions").session(admin)).andReturn();
        assertEquals(200, permissions.getResponse().getStatus());
        assertTrue(body(permissions).get("data").toString().contains("user:manage"));

        MvcResult created = mvc.perform(post("/api/roles").session(admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"HELPER\",\"name\":\"协助人员\"}"))
            .andReturn();
        assertEquals(200, created.getResponse().getStatus());
        long roleId = body(created).get("data").get("id").asLong();

        assertEquals(200, mvc.perform(put("/api/roles/" + roleId + "/permissions").session(admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"permissionCodes\":[\"article:create\"]}"))
            .andReturn().getResponse().getStatus());
        MvcResult detail = mvc.perform(get("/api/roles/" + roleId).session(admin)).andReturn();
        assertEquals(200, detail.getResponse().getStatus());
        assertTrue("角色权限应已保存: " + detail.getResponse().getContentAsString(),
            body(detail).get("data").get("permissions").toString().contains("article:create"));

        assertEquals("重复角色代码应冲突", 409, mvc.perform(post("/api/roles").session(admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"HELPER\",\"name\":\"重复角色\"}"))
            .andReturn().getResponse().getStatus());
        assertEquals("无效权限代码应 400", 400, mvc.perform(put("/api/roles/" + roleId + "/permissions")
            .session(admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"permissionCodes\":[\"not:exist\"]}")).andReturn().getResponse().getStatus());
    }

    @Test public void adminRoleCannotLoseManagePermissions() throws Exception {
        MockHttpSession admin = adminSession();
        MvcResult roles = mvc.perform(get("/api/roles").session(admin)).andReturn();
        long adminRoleId = -1;
        for (JsonNode node : body(roles).get("data")) {
            if ("ADMIN".equals(node.get("code").asText())) {
                adminRoleId = node.get("id").asLong();
            }
        }
        assertTrue("应能定位 ADMIN 角色", adminRoleId > 0);
        assertEquals("不得移除管理员角色的用户/角色管理权限", 409,
            mvc.perform(put("/api/roles/" + adminRoleId + "/permissions").session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissionCodes\":[\"article:create\"]}"))
                .andReturn().getResponse().getStatus());
    }

    @Test public void unknownRoleOrUserIsRejected() throws Exception {
        JsonNode student = register("stu14", "学生戊");
        long studentId = student.get("id").asLong();
        MockHttpSession admin = adminSession();
        assertEquals(400, mvc.perform(put("/api/users/" + studentId + "/roles").session(admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"NOPE\"]}"))
            .andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(get("/api/users/999999").session(admin)).andReturn()
            .getResponse().getStatus());
        assertEquals(404, mvc.perform(get("/api/roles/999999").session(admin)).andReturn()
            .getResponse().getStatus());
    }
}
