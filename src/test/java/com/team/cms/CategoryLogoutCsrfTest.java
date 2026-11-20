package com.team.cms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cms.auth.AuthConstants;
import com.team.cms.auth.service.AuthService;
import com.team.cms.config.SpringConfig;
import com.team.cms.config.SpringMvcConfig;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextHierarchy({
    @ContextConfiguration(name="root", classes=SpringConfig.class),
    @ContextConfiguration(name="mvc", classes=SpringMvcConfig.class)
})
@TestPropertySource(properties={
    "jdbc.driver=org.h2.Driver", "jdbc.url=jdbc:h2:mem:category_logout_csrf;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "jdbc.username=sa", "jdbc.password="
})
@Sql({"/auth-schema-test.sql","/article-schema-test.sql","/schema-category.sql"})
public class CategoryLogoutCsrfTest {
    @Autowired WebApplicationContext context;
    @Autowired AuthService auth;
    @Autowired DataSource dataSource;
    private MockMvc mvc;
    private JdbcTemplate jdbc;
    private MockHttpSession session, other;
    private final ObjectMapper json = new ObjectMapper();

    @Before public void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc = new JdbcTemplate(dataSource);
        auth.ensureAdmin("csrfadmin","isolated-csrf-password");
        session = login();
        other = login();
    }

    private MockHttpSession login() throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(Map.of("username","csrfadmin","password","isolated-csrf-password"))))
            .andReturn();
        assertEquals(200, result.getResponse().getStatus());
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String token(MockHttpSession user) {
        return (String) user.getAttribute(AuthConstants.SESSION_CSRF_TOKEN);
    }

    private MockHttpServletRequestBuilder write(String method, String path, String body, String token) {
        var request = request(HttpMethod.valueOf(method), path).session(session);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) request.header(AuthConstants.CSRF_HEADER, token);
        return request;
    }

    @Test public void allCategoryMutationsRejectMissingWrongAndOtherSessionTokensWithoutWriting() throws Exception {
        int created = mvc.perform(write("POST","/api/categories","{\"name\":\"Original\",\"sortOrder\":0}",token(session)))
            .andReturn().getResponse().getStatus();
        assertEquals(200, created);
        long id = jdbc.queryForObject("SELECT id FROM cms_category WHERE name='Original'",Long.class);
        List<Map<String,Object>> before = jdbc.queryForList("SELECT * FROM cms_category ORDER BY id");
        String path = "/api/categories/" + id;
        String[][] writes = {
            {"POST","/api/categories","{\"name\":\"Another\",\"sortOrder\":0}"},
            {"PUT",path,"{\"name\":\"Renamed\"}"},
            {"PUT",path+"/parent","{\"parentId\":null}"},
            {"PUT",path+"/sort","{\"sortOrder\":10}"},
            {"PUT",path+"/enabled","{\"enabled\":false}"},
            {"DELETE",path,null}
        };
        for (String[] operation : writes) {
            for (String invalid : new String[]{null,"invalid-token",token(other)}) {
                assertEquals(operation[0]+" "+operation[1],403,
                    mvc.perform(write(operation[0],operation[1],operation[2],invalid))
                        .andReturn().getResponse().getStatus());
                assertEquals(before,jdbc.queryForList("SELECT * FROM cms_category ORDER BY id"));
            }
        }
        for (String[] operation : writes) {
            assertEquals(operation[0]+" "+operation[1],200,
                mvc.perform(write(operation[0],operation[1],operation[2],token(session)))
                    .andReturn().getResponse().getStatus());
        }
        assertEquals(1,jdbc.queryForObject("SELECT deleted FROM cms_category WHERE id=?",Integer.class,id).intValue());
    }

    @Test public void refusedLogoutPreservesSessionAndCorrectTokenInvalidatesIt() throws Exception {
        for (String invalid : new String[]{null,"invalid-token",token(other)}) {
            assertEquals(403,mvc.perform(write("POST","/api/auth/logout",null,invalid))
                .andReturn().getResponse().getStatus());
            assertEquals(200,mvc.perform(get("/api/auth/me").session(session)).andReturn().getResponse().getStatus());
        }
        assertEquals(200,mvc.perform(write("POST","/api/auth/logout",null,token(session)))
            .andReturn().getResponse().getStatus());
        assertTrue(session.isInvalid());
        assertEquals(401,mvc.perform(get("/api/auth/me").session(session)).andReturn().getResponse().getStatus());
    }
}
