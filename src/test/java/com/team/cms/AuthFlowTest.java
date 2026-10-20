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

/** 郭金滔 GJT R1 身份流程验收测试：注册/登录/退出/me + 拦截器 + 权限切面。 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextHierarchy({@ContextConfiguration(name = "root", classes = SpringConfig.class),
    @ContextConfiguration(name = "mvc", classes = SpringMvcConfig.class)})
@TestPropertySource(properties = {"jdbc.driver=org.h2.Driver",
    "jdbc.url=jdbc:h2:mem:auth;MODE=MySQL;DB_CLOSE_DELAY=-1", "jdbc.username=sa", "jdbc.password="})
@Sql("/auth-schema-test.sql")
public class AuthFlowTest {
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

    private MvcResult register(String username, String displayName, String password) throws Exception {
        String payload = json.writeValueAsString(java.util.Map.of(
            "username", username, "displayName", displayName, "password", password));
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(payload)).andReturn();
    }

    private MockHttpSession login(String username, String password) throws Exception {
        String payload = json.writeValueAsString(java.util.Map.of("username", username, "password", password));
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(payload)).andReturn();
        assertEquals("登录应成功: " + result.getResponse().getContentAsString(),
            200, result.getResponse().getStatus());
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test public void registerAssignsEditorRoleAndIgnoresForgedRoleFields() throws Exception {
        String payload = "{\"username\":\"stu01\",\"displayName\":\"学生甲\",\"password\":\"password123\","
            + "\"role\":\"ADMIN\",\"roles\":[\"ADMIN\"]}";
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(payload)).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        JsonNode data = body(result).get("data");
        assertTrue("roles 应含 EDITOR: " + data, data.get("roles").toString().contains("EDITOR"));
        assertFalse("伪造的 ADMIN 角色应被忽略: " + data, data.get("roles").toString().contains("ADMIN"));
    }

    @Test public void registerRejectsDuplicateUsername() throws Exception {
        register("stu02", "学生乙", "password123");
        MvcResult duplicate = register("stu02", "学生乙二号", "password123");
        assertEquals(409, duplicate.getResponse().getStatus());
    }

    @Test public void loginRejectsWrongPasswordAndUnknownUserUniformly() throws Exception {
        register("stu03", "学生丙", "password123");
        String payload = json.writeValueAsString(java.util.Map.of("username", "stu03", "password", "wrongpass99"));
        MvcResult wrong = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(payload)).andReturn();
        assertEquals(401, wrong.getResponse().getStatus());

        String unknown = json.writeValueAsString(java.util.Map.of("username", "nobody01", "password", "password123"));
        MvcResult missing = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(unknown)).andReturn();
        assertEquals(401, missing.getResponse().getStatus());
        assertEquals("错误与未知用户的提示应一致",
            body(wrong).get("message").asText(), body(missing).get("message").asText());
    }

    @Test public void meWithoutSessionReturns401() throws Exception {
        assertEquals(401, mvc.perform(get("/api/auth/me")).andReturn().getResponse().getStatus());
    }

    @Test public void loginThenMeReturnsCurrentUser() throws Exception {
        register("stu04", "学生丁", "password123");
        MockHttpSession session = login("stu04", "password123");
        MvcResult result = mvc.perform(get("/api/auth/me").session(session)).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        JsonNode data = body(result).get("data");
        assertEquals("stu04", data.get("username").asText());
        assertEquals("学生丁", data.get("displayName").asText());
        assertTrue(data.get("roles").toString().contains("EDITOR"));
    }

    @Test public void logoutInvalidatesSession() throws Exception {
        register("stu05", "学生戊", "password123");
        MockHttpSession session = login("stu05", "password123");
        assertEquals(200, mvc.perform(post("/api/auth/logout").session(session)).andReturn()
            .getResponse().getStatus());
        assertEquals(401, mvc.perform(get("/api/auth/me").session(session)).andReturn()
            .getResponse().getStatus());
    }

    @Test public void editorLacksManagePermissionReturns403() throws Exception {
        register("stu06", "学生己", "password123");
        MockHttpSession session = login("stu06", "password123");
        assertEquals(403, mvc.perform(get("/api/auth-probe/admin-only").session(session))
            .andReturn().getResponse().getStatus());
    }

    @Test public void adminBypassesPermissionCheck() throws Exception {
        authService.ensureAdmin("admin", "admin12345");
        MockHttpSession session = login("admin", "admin12345");
        MvcResult result = mvc.perform(get("/api/auth-probe/admin-only").session(session)).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertEquals("granted", body(result).get("data").asText());
    }
}
