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

    @Test public void editReviewResubmitAndVersionRules() throws Exception {
        MockHttpSession owner=loginNewUser("r2owner");
        MockHttpSession other=loginNewUser("r2other");
        MockHttpSession reviewer=loginNewUser("r2reviewer");
        JdbcTemplate jdbc=new JdbcTemplate(dataSource);
        long reviewerId=((CurrentUser)reviewer.getAttribute(AuthConstants.SESSION_USER)).getId();
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?",reviewerId);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE code='REVIEWER'",reviewerId);
        String credentials=json.writeValueAsString(Map.of("username","r2reviewer","password","password123"));
        MvcResult login=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(credentials)).andReturn();
        reviewer=(MockHttpSession)login.getRequest().getSession(false);
        long categoryId=category(true);
        long id=insertDraft(((CurrentUser)owner.getAttribute(AuthConstants.SESSION_USER)).getId());
        jdbc.update("UPDATE cms_article SET category_id=? WHERE id=?",categoryId,id);
        String edit=draftJson(categoryId).replaceFirst("}$",",\"version\":0}");
        String ownerToken=csrfToken(owner),reviewToken=csrfToken(reviewer);
        assertEquals(404,mvc.perform(get("/api/items/{id}/mine",id).session(other)).andReturn().getResponse().getStatus());
        MvcResult otherList=mvc.perform(get("/api/items/mine").session(other)).andReturn();
        assertEquals(200,otherList.getResponse().getStatus());
        assertEquals(0,json.readTree(otherList.getResponse().getContentAsByteArray()).at("/data/records").size());
        assertEquals(200,mvc.perform(put("/api/items/{id}",id).session(owner).header(AuthConstants.CSRF_HEADER,ownerToken)
            .contentType(MediaType.APPLICATION_JSON).content(edit)).andReturn().getResponse().getStatus());
        assertEquals(409,mvc.perform(put("/api/items/{id}",id).session(owner).header(AuthConstants.CSRF_HEADER,ownerToken)
            .contentType(MediaType.APPLICATION_JSON).content(edit)).andReturn().getResponse().getStatus());
        String version1="{\"version\":1}";
        assertEquals(200,mvc.perform(post("/api/items/{id}/submit",id).session(owner).header(AuthConstants.CSRF_HEADER,ownerToken)
            .contentType(MediaType.APPLICATION_JSON).content(version1)).andReturn().getResponse().getStatus());
        assertEquals(409,mvc.perform(put("/api/items/{id}",id).session(owner).header(AuthConstants.CSRF_HEADER,ownerToken)
            .contentType(MediaType.APPLICATION_JSON).content(edit)).andReturn().getResponse().getStatus());
        String reject="{\"version\":2,\"comment\":\"请补充地点细节\"}";
        assertEquals(403,mvc.perform(post("/api/reviews/{id}/reject",id).session(owner).header(AuthConstants.CSRF_HEADER,ownerToken)
            .contentType(MediaType.APPLICATION_JSON).content(reject)).andReturn().getResponse().getStatus());
        assertEquals(200,mvc.perform(post("/api/reviews/{id}/reject",id).session(reviewer).header(AuthConstants.CSRF_HEADER,reviewToken)
            .contentType(MediaType.APPLICATION_JSON).content(reject)).andReturn().getResponse().getStatus());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM cms_review_record WHERE article_id=?",Integer.class,id).intValue());
        assertEquals(200,mvc.perform(get("/api/items/{id}/reviews",id).session(owner)).andReturn().getResponse().getStatus());
        assertEquals(200,mvc.perform(put("/api/items/{id}",id).session(owner).header(AuthConstants.CSRF_HEADER,ownerToken)
            .contentType(MediaType.APPLICATION_JSON).content(edit.replace("\"version\":0","\"version\":3"))).andReturn().getResponse().getStatus());
        assertEquals(200,mvc.perform(post("/api/items/{id}/submit",id).session(owner).header(AuthConstants.CSRF_HEADER,ownerToken)
            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":4}")).andReturn().getResponse().getStatus());
        assertEquals(200,mvc.perform(post("/api/reviews/{id}/approve",id).session(reviewer).header(AuthConstants.CSRF_HEADER,reviewToken)
            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":5}")).andReturn().getResponse().getStatus());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM cms_review_record WHERE article_id=?",Integer.class,id).intValue());
        MvcResult approved=mvc.perform(get("/api/reviews/approved").session(reviewer)).andReturn();
        assertEquals(200,approved.getResponse().getStatus());
        assertEquals(1,json.readTree(approved.getResponse().getContentAsByteArray()).at("/data/records").size());
        assertEquals(200,mvc.perform(post("/api/reviews/{id}/publish",id).session(reviewer)
            .header(AuthConstants.CSRF_HEADER,reviewToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":6}")).andReturn().getResponse().getStatus());
        assertEquals("PUBLISHED",articleMapper.selectById(id).getStatus().name());
    }

    @Test public void failedReviewRecordRollsBackStatusAndSelfReviewIsForbidden() throws Exception {
        MockHttpSession reviewer=loginNewUser("rollbackReviewer");
        long reviewerId=((CurrentUser)reviewer.getAttribute(AuthConstants.SESSION_USER)).getId();
        JdbcTemplate jdbc=new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?",reviewerId);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE code='REVIEWER'",reviewerId);
        String credentials=json.writeValueAsString(Map.of("username","rollbackReviewer","password","password123"));
        reviewer=(MockHttpSession)mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(credentials)).andReturn().getRequest().getSession(false);
        String token=csrfToken(reviewer);
        long ownId=insertDraft(reviewerId);
        jdbc.update("UPDATE cms_article SET status='PENDING_REVIEW',version=1 WHERE id=?",ownId);
        assertEquals(403,mvc.perform(post("/api/reviews/{id}/approve",ownId).session(reviewer)
            .header(AuthConstants.CSRF_HEADER,token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":1}")).andReturn().getResponse().getStatus());
        MockHttpSession owner=loginNewUser("rollbackOwner");
        long id=insertDraft(((CurrentUser)owner.getAttribute(AuthConstants.SESSION_USER)).getId());
        long categoryId=category(true);
        jdbc.update("UPDATE cms_article SET status='PENDING_REVIEW',version=1,category_id=? WHERE id=?",categoryId,id);
        jdbc.execute("DROP TABLE cms_review_record");
        assertEquals(500,mvc.perform(post("/api/reviews/{id}/approve",id).session(reviewer)
            .header(AuthConstants.CSRF_HEADER,token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":1}")).andReturn().getResponse().getStatus());
        assertEquals("PENDING_REVIEW",articleMapper.selectById(id).getStatus().name());
        assertEquals(Integer.valueOf(1),articleMapper.selectById(id).getVersion());
    }

    @Test public void withdrawAndPublishedItemMustGoOfflineBeforeEditing() throws Exception {
        MockHttpSession owner=loginNewUser("transitionOwner");
        long ownerId=((CurrentUser)owner.getAttribute(AuthConstants.SESSION_USER)).getId();
        long categoryId=category(true),id=insertDraft(ownerId);
        JdbcTemplate jdbc=new JdbcTemplate(dataSource);
        jdbc.update("UPDATE cms_article SET category_id=?,status='PENDING_REVIEW',version=1 WHERE id=?",categoryId,id);
        String token=csrfToken(owner);
        assertEquals(200,mvc.perform(post("/api/items/{id}/withdraw",id).session(owner)
            .header(AuthConstants.CSRF_HEADER,token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":1}")).andReturn().getResponse().getStatus());
        assertEquals("DRAFT",articleMapper.selectById(id).getStatus().name());
        jdbc.update("UPDATE cms_article SET status='PUBLISHED',version=3 WHERE id=?",id);
        String edit=draftJson(categoryId).replaceFirst("}$",",\"version\":3}");
        assertEquals(409,mvc.perform(put("/api/items/{id}",id).session(owner)
            .header(AuthConstants.CSRF_HEADER,token).contentType(MediaType.APPLICATION_JSON)
            .content(edit)).andReturn().getResponse().getStatus());
        assertEquals(200,mvc.perform(post("/api/items/{id}/offline",id).session(owner)
            .header(AuthConstants.CSRF_HEADER,token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":3}")).andReturn().getResponse().getStatus());
        assertEquals(200,mvc.perform(post("/api/items/{id}/draft",id).session(owner)
            .header(AuthConstants.CSRF_HEADER,token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":4}")).andReturn().getResponse().getStatus());
        assertEquals(200,mvc.perform(put("/api/items/{id}",id).session(owner)
            .header(AuthConstants.CSRF_HEADER,token).contentType(MediaType.APPLICATION_JSON)
            .content(edit.replace("\"version\":3","\"version\":5"))).andReturn().getResponse().getStatus());
    }

    @Test public void specificSubmitPermissionIsRequiredEvenWithLegacyEditGrant() throws Exception {
        MockHttpSession first=loginNewUser("submitPermission");
        long authorId=((CurrentUser)first.getAttribute(AuthConstants.SESSION_USER)).getId();
        long categoryId=category(true),id=insertDraft(authorId);
        JdbcTemplate jdbc=new JdbcTemplate(dataSource);
        jdbc.update("UPDATE cms_article SET category_id=? WHERE id=?",categoryId,id);
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=(SELECT id FROM sys_role WHERE code='EDITOR') " +
            "AND permission_id=(SELECT id FROM sys_permission WHERE code='item:submit')");
        String credentials=json.writeValueAsString(Map.of("username","submitPermission","password","password123"));
        MockHttpSession session=(MockHttpSession)mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON).content(credentials)).andReturn().getRequest().getSession(false);
        String token=csrfToken(session);
        assertEquals(200,mvc.perform(get("/api/items/{id}/mine",id).session(session))
            .andReturn().getResponse().getStatus());
        assertEquals(403,mvc.perform(post("/api/items/{id}/submit",id).session(session)
            .header(AuthConstants.CSRF_HEADER,token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":0}")).andReturn().getResponse().getStatus());
        assertEquals("DRAFT",articleMapper.selectById(id).getStatus().name());
    }

    @Autowired private com.team.cms.category.service.CategoryService categoryService;
    @Autowired private com.team.cms.category.service.CategoryAccessService categoryAccess;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test public void actualArticleReferencePreventsCategoryDeletion() throws Exception {
        MockHttpSession owner=loginNewUser("categoryReferenceOwner");
        long categoryId=category(true);
        long id=insertDraft(((CurrentUser)owner.getAttribute(AuthConstants.SESSION_USER)).getId());
        JdbcTemplate jdbc=new JdbcTemplate(dataSource);
        jdbc.update("UPDATE cms_article SET category_id=? WHERE id=?",categoryId,id);
        try { categoryService.delete(categoryId); fail("Referenced category must remain"); }
        catch (com.team.cms.common.exception.BusinessException expected) {
            assertEquals(org.springframework.http.HttpStatus.CONFLICT,expected.getStatus());
        }
        assertNotNull(categoryMapper.selectById(categoryId));
        jdbc.update("UPDATE cms_article SET deleted=1 WHERE id=?",id);
        categoryService.delete(categoryId);
        assertNull(categoryMapper.selectById(categoryId));
    }

    @Test public void bindingAndDeletingSameCategoryUseTheSameDatabaseLock() throws Exception {
        MockHttpSession owner=loginNewUser("categoryLockOwner");
        long author=((CurrentUser)owner.getAttribute(AuthConstants.SESSION_USER)).getId();
        long categoryId=category(true);
        java.util.concurrent.CountDownLatch locked=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch deleting=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<?> binding=pool.submit(() -> {
                new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
                    categoryAccess.requireBindable(categoryId); locked.countDown();
                    try { if (!release.await(5,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                    long id=insertDraft(author);
                    new JdbcTemplate(dataSource).update("UPDATE cms_article SET category_id=? WHERE id=?",categoryId,id);
                    return null;
                });
            });
            assertTrue(locked.await(5,java.util.concurrent.TimeUnit.SECONDS));
            java.util.concurrent.Future<?> deletion=pool.submit(() -> { deleting.countDown(); categoryService.delete(categoryId); });
            assertTrue(deleting.await(5,java.util.concurrent.TimeUnit.SECONDS));
            try { deletion.get(150,java.util.concurrent.TimeUnit.MILLISECONDS); fail("Deletion must wait for binding transaction"); }
            catch (java.util.concurrent.TimeoutException expected) { /* category row remains locked */ }
            release.countDown(); binding.get(5,java.util.concurrent.TimeUnit.SECONDS);
            try { deletion.get(5,java.util.concurrent.TimeUnit.SECONDS); fail("Committed reference must reject deletion"); }
            catch (java.util.concurrent.ExecutionException expected) {
                assertTrue(expected.getCause() instanceof com.team.cms.common.exception.BusinessException);
                assertEquals(org.springframework.http.HttpStatus.CONFLICT,
                    ((com.team.cms.common.exception.BusinessException)expected.getCause()).getStatus());
            }
            assertNotNull(categoryMapper.selectById(categoryId));
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test public void legacyAndCanonicalReviewRoutesShareRulesAndReadonlyCannotDecide() throws Exception {
        MockHttpSession owner=loginNewUser("contractOwner"),reviewer=loginNewUser("contractReviewer");
        long author=((CurrentUser)owner.getAttribute(AuthConstants.SESSION_USER)).getId();
        long reviewerId=((CurrentUser)reviewer.getAttribute(AuthConstants.SESSION_USER)).getId();
        JdbcTemplate jdbc=new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?",reviewerId);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE code='REVIEWER'",reviewerId);
        long categoryId=category(true),id=insertDraft(author);
        jdbc.update("UPDATE cms_article SET category_id=?,status='PENDING_REVIEW',version=1 WHERE id=?",categoryId,id);
        String token=csrfToken(reviewer);
        String decision="{\"version\":1,\"decision\":\"REJECTED\",\"comment\":\"请补充地点\"}";
        assertEquals(403,mvc.perform(post("/api/reviews/{id}/decision",id).session(reviewer)
            .contentType(MediaType.APPLICATION_JSON).content(decision)).andReturn().getResponse().getStatus());
        assertEquals(200,mvc.perform(post("/api/reviews/{id}/decision",id).session(reviewer)
            .header(AuthConstants.CSRF_HEADER,token).contentType(MediaType.APPLICATION_JSON).content(decision))
            .andReturn().getResponse().getStatus());
        assertEquals("REJECTED",articleMapper.selectById(id).getStatus().name());
        jdbc.update("UPDATE cms_article SET status='PENDING_REVIEW' WHERE id=?",id);
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=(SELECT id FROM sys_role WHERE code='REVIEWER') " +
            "AND permission_id IN (SELECT id FROM sys_permission WHERE code IN ('review:handle','review:decide'))");
        assertEquals(200,mvc.perform(get("/api/reviews/pending").session(reviewer)).andReturn().getResponse().getStatus());
        assertEquals(403,mvc.perform(post("/api/reviews/{id}/approve",id).session(reviewer)
            .header(AuthConstants.CSRF_HEADER,token).contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}"))
            .andReturn().getResponse().getStatus());
        assertEquals("PENDING_REVIEW",articleMapper.selectById(id).getStatus().name());
    }

    @Test public void categoryPermissionMigrationIsIdempotentAndDoesNotGrantStudentsManagement() throws Exception {
        try (java.sql.Connection connection=dataSource.getConnection()) {
            org.springframework.core.io.FileSystemResource script=new org.springframework.core.io.FileSystemResource(
                "sql/migrations/005-r2-category-permissions.sql");
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                new org.springframework.core.io.support.EncodedResource(script, "UTF-8"));
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                new org.springframework.core.io.support.EncodedResource(script, "UTF-8"));
        }
        JdbcTemplate jdbc=new JdbcTemplate(dataSource);
        assertEquals(7,jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission WHERE code LIKE 'category:%'",Integer.class).intValue());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id " +
            "JOIN sys_permission p ON p.id=rp.permission_id WHERE r.code='EDITOR' AND p.code LIKE 'category:%'",Integer.class).intValue());
    }
}
