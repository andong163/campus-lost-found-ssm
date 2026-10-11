package com.team.cms;

import com.fasterxml.jackson.databind.*;
import com.team.cms.common.web.RequestLogFilter;
import com.team.cms.config.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.*;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextHierarchy({@ContextConfiguration(name = "root", classes = SpringConfig.class),
    @ContextConfiguration(name = "mvc", classes = SpringMvcConfig.class)})
@TestPropertySource(properties = {"jdbc.driver=org.h2.Driver", "jdbc.url=jdbc:h2:mem:foundation;MODE=MySQL;DB_CLOSE_DELAY=-1", "jdbc.username=sa", "jdbc.password="})
@Sql("/schema-test.sql")
public class WebContractTest {
    @Autowired private WebApplicationContext context;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();
    @Before public void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(new RequestLogFilter())
            .addDispatcherServletCustomizer(servlet -> servlet.setThrowExceptionIfNoHandlerFound(true)).build();
    }
    private JsonNode assertCode(MockHttpServletResponse response, int code) throws Exception {
        assertEquals(code, response.getStatus());
        JsonNode body = json.readTree(response.getContentAsString());
        assertEquals(code, body.get("code").asInt());
        assertTrue(body.has("message")); assertTrue(body.has("data"));
        assertNotNull(response.getHeader("X-Request-Id")); return body;
    }
    @Test public void healthAndReadinessExerciseBothContextsAndMapper() throws Exception {
        assertCode(mvc.perform(get("/api/system/health")).andReturn().getResponse(), 200);
        assertCode(mvc.perform(get("/api/system/readiness")).andReturn().getResponse(), 200);
    }
    @Test public void paginationRejectsZeroOversizedAndNonNumericParameters() throws Exception {
        for (String query : new String[]{"page=0", "size=101", "size=0", "page=abc"}) {
            assertCode(mvc.perform(get("/__test/page?" + query)).andReturn().getResponse(), 400);
        }
    }
    @Test public void jsonBodyValidationAndMalformedJsonReturn400() throws Exception {
        assertCode(mvc.perform(post("/__test/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}")).andReturn().getResponse(), 400);
        assertCode(mvc.perform(post("/__test/body").contentType(MediaType.APPLICATION_JSON).content("{")).andReturn().getResponse(), 400);
    }
    @Test public void unknownRoutesAndWrongMethodsUseUnifiedErrors() throws Exception {
        assertCode(mvc.perform(get("/not-a-route")).andReturn().getResponse(), 404);
        assertCode(mvc.perform(post("/api/system/health")).andReturn().getResponse(), 405);
    }
    @Test public void businessErrorsAndUnexpectedFailuresDoNotLeakDetails() throws Exception {
        assertCode(mvc.perform(get("/__test/conflict")).andReturn().getResponse(), 409);
        MockHttpServletResponse failure = mvc.perform(get("/__test/failure")).andReturn().getResponse();
        assertCode(failure, 500); assertFalse(failure.getContentAsString().contains("secret-not-for-client"));
    }
    @Test public void readinessReportsUnavailableWhenRoleInitializationIsMissing() throws Exception {
        JdbcTemplate sql = new JdbcTemplate(context.getBean(DataSource.class));
        sql.update("DELETE FROM sys_role");
        sql.update("INSERT INTO sys_role(code) VALUES ('OTHER1'), ('OTHER2'), ('OTHER3'), ('OTHER4')");
        MockHttpServletResponse response = mvc.perform(get("/api/system/readiness")).andReturn().getResponse();
        assertCode(response, 503); assertFalse(response.getContentAsString().contains("jdbc:"));
    }
}
