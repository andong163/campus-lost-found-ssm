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
    @Test public void parentAspectActuallyInterceptsMvcController() throws Exception {
        assertCode(mvc.perform(get("/__test/denied-by-aspect")).andReturn().getResponse(), 403);
    }
    @Test public void batchValidationRejectsEmptyDuplicateInvalidAndFractionalIds() throws Exception {
        for (String body : new String[]{"{}", "{\"ids\":[]}", "{\"ids\":[1,1]}",
                "{\"ids\":[0]}", "{\"ids\":[null]}", "{\"ids\":[1.9]}"}) {
            assertCode(mvc.perform(post("/__test/batch").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse(), 400);
        }
        String oversized = "{\"ids\":" + java.util.stream.LongStream.rangeClosed(1, 101)
            .boxed().collect(java.util.stream.Collectors.toList()).toString() + "}";
        assertCode(mvc.perform(post("/__test/batch").contentType(MediaType.APPLICATION_JSON).content(oversized))
            .andReturn().getResponse(), 400);
        JsonNode body = assertCode(mvc.perform(post("/__test/batch").contentType(MediaType.APPLICATION_JSON)
            .content("{\"ids\":[1,2]}")).andReturn().getResponse(), 200);
        assertEquals(2, body.get("data").asInt());
    }
    @Test public void readinessDetectsMissingClaimTableWithoutLeakingSql() throws Exception {
        JdbcTemplate sql = new JdbcTemplate(context.getBean(DataSource.class));
        sql.execute("DROP TABLE cms_claim_request");
        MockHttpServletResponse response = mvc.perform(get("/api/system/readiness")).andReturn().getResponse();
        assertCode(response, 503); assertFalse(response.getContentAsString().contains("cms_claim_request"));
    }
    @Test public void readinessReturns503WhenConnectionCannotBeAcquired() throws Exception {
        com.alibaba.druid.pool.DruidDataSource source = context.getBean(com.alibaba.druid.pool.DruidDataSource.class);
        source.setEnable(false);
        try {
            assertCode(mvc.perform(get("/api/system/readiness")).andReturn().getResponse(), 503);
        } finally { source.setEnable(true); }
    }
    @Test public void versionAndEnumContractsRejectInvalidJsonValues() throws Exception {
        for (String body : new String[]{"{\"itemType\":\"FOUND\"}",
                "{\"version\":-1,\"itemType\":\"FOUND\"}", "{\"version\":null,\"itemType\":\"FOUND\"}",
                "{\"version\":1.5,\"itemType\":\"FOUND\"}", "{\"version\":2147483648,\"itemType\":\"FOUND\"}",
                "{\"version\":0,\"itemType\":0}"}) {
            assertCode(mvc.perform(post("/__test/version").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse(), 400);
        }
        JsonNode valid = assertCode(mvc.perform(post("/__test/version").contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":0,\"itemType\":\"FOUND\"}")).andReturn().getResponse(), 200);
        assertEquals(0, valid.get("data").asInt());
    }
    @Test public void databaseExceptionsKeepStatusContractWithoutLeakingSql() throws Exception {
        MockHttpServletResponse conflict = mvc.perform(get("/__test/database-conflict")).andReturn().getResponse();
        assertCode(conflict, 409);
        assertFalse(conflict.getContentAsString().contains("private-sql-marker"));
        MockHttpServletResponse unavailable = mvc.perform(get("/__test/dependency-failure")).andReturn().getResponse();
        assertCode(unavailable, 503);
        assertFalse(unavailable.getContentAsString().contains("private-sql-marker"));
    }
    @Test public void modulePageAndAssetsServeWithoutChangingApi404Contract() throws Exception {
        MockHttpServletResponse page = mvc.perform(get("/pages/index.html")).andReturn().getResponse();
        assertEquals(200, page.getStatus());
        assertTrue(new String(page.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8).contains("校园失物招领"));
        MockHttpServletResponse css = mvc.perform(get("/assets/css/base.css")).andReturn().getResponse();
        assertEquals(200, css.getStatus());
        assertTrue(css.getContentType().startsWith("text/css"));
        assertCode(mvc.perform(get("/api/not-found")).andReturn().getResponse(), 404);
    }
    @Test public void nestedSharedUiAndModuleManifestsStayPublicWhileBusinessApisStayProtected() throws Exception {
        for (String path : new String[]{"/pages/examples/shared-components.html", "/assets/js/api.js", "/assets/js/shell.js", "/assets/js/pagination.js"}) {
            assertEquals(path, 200, mvc.perform(get(path)).andReturn().getResponse().getStatus());
        }
        for (String module : new String[]{"auth", "category", "items", "review", "claims", "audit"}) {
            MockHttpServletResponse response = mvc.perform(get("/pages/" + module + "/module.json")).andReturn().getResponse();
            assertEquals(200, response.getStatus());
            JsonNode manifest = json.readTree(response.getContentAsByteArray());
            assertEquals(module, manifest.get("module").asText());
            assertTrue(manifest.get("entries").isArray());
        }
        assertCode(mvc.perform(get("/api/operation-logs")).andReturn().getResponse(), 401);
        assertCode(mvc.perform(get("/api/auth/me")).andReturn().getResponse(), 401);
    }
}
