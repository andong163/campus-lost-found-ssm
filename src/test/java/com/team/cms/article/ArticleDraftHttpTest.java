package com.team.cms.article;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cms.article.dto.CreateDraftRequest;
import com.team.cms.article.entity.Article;
import com.team.cms.article.mapper.ArticleMapper;
import com.team.cms.auth.AuthConstants;
import com.team.cms.auth.security.CurrentUser;
import com.team.cms.category.entity.Category;
import com.team.cms.category.mapper.CategoryMapper;
import com.team.cms.config.SpringConfig;
import com.team.cms.config.SpringMvcConfig;
import java.time.LocalDateTime;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static com.team.cms.common.domain.ItemType.LOST;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** 使用独立 H2 库和真实登录会话验证本人草稿接口。 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextHierarchy({@ContextConfiguration(name = "root", classes = SpringConfig.class),
    @ContextConfiguration(name = "mvc", classes = SpringMvcConfig.class)})
@TestPropertySource(properties = {"jdbc.driver=org.h2.Driver",
    "jdbc.url=jdbc:h2:mem:article_http_r1;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "jdbc.username=sa", "jdbc.password="})
@Sql({"/auth-schema-test.sql", "/article-schema-test.sql", "/schema-category.sql"})
public class ArticleDraftHttpTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ArticleMapper articleMapper;
    @Autowired private CategoryMapper categoryMapper;
    @Autowired private DataSource dataSource;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Before public void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private MockHttpSession loginNewUser(String username) throws Exception {
        String registration = json.writeValueAsString(Map.of("username", username,
            "displayName", "测试学生", "password", "password123"));
        assertEquals(200, mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(registration)).andReturn().getResponse().getStatus());
        String credentials = json.writeValueAsString(Map.of("username", username, "password", "password123"));
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(credentials)).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private long insertDraft(long authorId) {
        CreateDraftRequest request = new CreateDraftRequest();
        request.setTitle("丢失校园卡");
        request.setContent("在图书馆丢失一张校园卡，请联系我。");
        request.setItemType(LOST);
        request.setCategoryId(202L);
        request.setEventTime(LocalDateTime.now().minusHours(1));
        request.setLocation("学校图书馆");
        request.setContactInfo("本人电话 12345678901");
        Article article = request.toNewArticle(authorId);
        assertEquals(1, articleMapper.insert(article));
        return article.getId();
    }

    private long category(boolean enabled) {
        Category category = new Category();
        category.setName(enabled ? "可用分类" : "停用分类");
        category.setSortOrder(0);
        category.setEnabled(enabled ? 1 : 0);
        assertEquals(1, categoryMapper.insert(category));
        return category.getId();
    }

    private String draftJson(long categoryId) throws Exception {
        return json.writeValueAsString(Map.of(
            "title", "丢失校园卡", "content", "在图书馆丢失一张校园卡，请联系我。",
            "itemType", "LOST", "categoryId", categoryId,
            "eventTime", LocalDateTime.now().minusHours(1).toString(),
            "location", "学校图书馆", "contactInfo", "本人电话 12345678901"));
    }

    private String csrfToken(MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        return json.readTree(result.getResponse().getContentAsByteArray()).get("data").asText();
    }

    @Test public void createUsesSessionAuthorAndServerControlledFields() throws Exception {
        MockHttpSession owner = loginNewUser("create01");
        long categoryId = category(true);
        String payload = draftJson(categoryId);
        String token = csrfToken(owner);
        MvcResult result = mvc.perform(post("/api/items").session(owner)
            .header(AuthConstants.CSRF_HEADER, token)
            .contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        JsonNode data = json.readTree(result.getResponse().getContentAsByteArray()).get("data");
        long id = data.get("id").asLong();
        Article saved = articleMapper.selectById(id);
        assertEquals(((CurrentUser) owner.getAttribute(AuthConstants.SESSION_USER)).getId(), saved.getAuthorId());
        assertEquals("DRAFT", saved.getStatus().name());
        assertEquals("OPEN", saved.getResolutionStatus().name());
        assertEquals(Integer.valueOf(0), saved.getVersion());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());

        String forged = payload.replaceFirst("}$",
            ",\"authorId\":999,\"status\":\"PUBLISHED\",\"version\":99}");
        assertEquals(400, mvc.perform(post("/api/items").session(owner)
            .header(AuthConstants.CSRF_HEADER, token)
            .contentType(MediaType.APPLICATION_JSON).content(forged))
            .andReturn().getResponse().getStatus());
        assertEquals(1, new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM cms_article", Integer.class).intValue());
    }

    @Test public void createRejectsMissingOrDisabledCategoryAndUnauthenticatedUser() throws Exception {
        MockHttpSession owner = loginNewUser("category01");
        long disabledId = category(false);
        String token = csrfToken(owner);
        assertEquals(409, mvc.perform(post("/api/items").session(owner)
            .header(AuthConstants.CSRF_HEADER, token)
            .contentType(MediaType.APPLICATION_JSON).content(draftJson(disabledId)))
            .andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(post("/api/items").session(owner)
            .header(AuthConstants.CSRF_HEADER, token)
            .contentType(MediaType.APPLICATION_JSON).content(draftJson(99999)))
            .andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(post("/api/items")
            .contentType(MediaType.APPLICATION_JSON).content(draftJson(disabledId)))
            .andReturn().getResponse().getStatus());
        assertEquals(0, new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM cms_article", Integer.class).intValue());
    }

    @Test public void createRejectsMissingOrWrongCsrfToken() throws Exception {
        MockHttpSession owner = loginNewUser("csrf01");
        MockHttpSession other = loginNewUser("csrf02");
        long categoryId = category(true);
        String payload = draftJson(categoryId);
        assertEquals(403, mvc.perform(post("/api/items").session(owner)
            .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(post("/api/items").session(owner)
            .header(AuthConstants.CSRF_HEADER, "wrong-token")
            .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(post("/api/items").session(owner)
            .header(AuthConstants.CSRF_HEADER, csrfToken(other))
            .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andReturn().getResponse().getStatus());
        assertEquals(0, new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM cms_article", Integer.class).intValue());
    }

    @Test public void ownerCanReadPrivateDraftButOtherUserCannot() throws Exception {
        MockHttpSession owner = loginNewUser("owner01");
        MockHttpSession other = loginNewUser("other01");
        long id = insertDraft(((CurrentUser) owner.getAttribute(AuthConstants.SESSION_USER)).getId());

        MvcResult own = mvc.perform(get("/api/items/{id}", id).session(owner)).andReturn();
        assertEquals(200, own.getResponse().getStatus());
        JsonNode data = json.readTree(own.getResponse().getContentAsByteArray()).get("data");
        assertEquals(id, data.get("id").asLong());
        assertEquals("DRAFT", data.get("status").asText());
        assertEquals("本人电话 12345678901", data.get("contactInfo").asText());

        assertEquals(404, mvc.perform(get("/api/items/{id}", id).session(other))
            .andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(get("/api/items/{id}", id))
            .andReturn().getResponse().getStatus());
    }

    @Test public void userWithoutArticleEditPermissionGets403() throws Exception {
        MockHttpSession viewer = loginNewUser("viewer01");
        long userId = ((CurrentUser) viewer.getAttribute(AuthConstants.SESSION_USER)).getId();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) SELECT ?, id FROM sys_role WHERE code = 'VIEWER'", userId);
        String credentials = json.writeValueAsString(Map.of("username", "viewer01", "password", "password123"));
        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(credentials)).andReturn();
        assertEquals(200, login.getResponse().getStatus());
        MockHttpSession viewerSession = (MockHttpSession) login.getRequest().getSession(false);
        assertEquals(403, mvc.perform(get("/api/items/{id}", 1).session(viewerSession))
            .andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(post("/api/items").session(viewerSession)
            .contentType(MediaType.APPLICATION_JSON).content(draftJson(1)))
            .andReturn().getResponse().getStatus());
    }
}
