package com.team.cms.article;

import com.team.cms.article.dto.CreateDraftRequest;
import com.team.cms.article.dto.PublicArticleQuery;
import com.team.cms.article.entity.Article;
import com.team.cms.article.mapper.ArticleMapper;
import com.team.cms.article.service.PublicArticleService;
import com.team.cms.auth.security.CurrentUser;
import com.team.cms.auth.security.CurrentUserContext;
import com.team.cms.category.mapper.CategoryMapper;
import com.team.cms.common.domain.ArticleStatus;
import com.team.cms.common.domain.ItemType;
import com.team.cms.review.service.ItemWorkflowService;
import com.team.cms.config.SpringConfig;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes=SpringConfig.class)
@TestPropertySource(properties={"jdbc.driver=org.h2.Driver","jdbc.url=jdbc:h2:mem:article_r3;MODE=MySQL;DB_CLOSE_DELAY=-1","jdbc.username=sa","jdbc.password="})
@Sql({"/auth-schema-test.sql","/article-schema-test.sql","/schema-category.sql"})
public class ArticleR3Test {
    @Autowired ArticleMapper articles;
    @Autowired CategoryMapper categories;
    @Autowired PublicArticleService publicItems;
    @Autowired ItemWorkflowService workflow;
    @Autowired DataSource dataSource;
    private JdbcTemplate jdbc() { return new JdbcTemplate(dataSource); }
    @Before public void categoryFixture() {
        jdbc().update("INSERT INTO cms_category(id,name,sort_order,enabled,deleted,version) VALUES (201,'物品',0,1,0,0)");
        jdbc().update("INSERT INTO cms_category(id,name,parent_id,sort_order,enabled,deleted,version) VALUES (202,'书包',201,0,1,0,0)");
        jdbc().execute("CREATE TABLE IF NOT EXISTS sys_operation_log (id BIGINT AUTO_INCREMENT PRIMARY KEY,operator_id BIGINT,operator_name VARCHAR(80),module VARCHAR(50),action VARCHAR(80),target_id BIGINT,request_id VARCHAR(36),result VARCHAR(20),elapsed_ms BIGINT,created_at TIMESTAMP)");
    }
    private long article(String title, ArticleStatus status, long category) {
        CreateDraftRequest request=new CreateDraftRequest();
        request.setTitle(title);request.setContent("在图书馆附近发现物品，请描述特征确认。力求找回失主。");
        request.setItemType(ItemType.FOUND);request.setCategoryId(category);
        request.setEventTime(LocalDateTime.now().minusDays(1));request.setLocation("学校图书馆");
        request.setContactInfo("私密联系方式 12345678901");
        Article value=request.toNewArticle(1L);articles.insert(value);
        jdbc().update("UPDATE cms_article SET status=?,published_at=CURRENT_TIMESTAMP WHERE id=?",status.name(),value.getId());
        return value.getId();
    }
    @Test public void publicScopePrivacyAndCombinedFilters() {
        long visible=article("找到蓝色书包",ArticleStatus.PUBLISHED,202);
        long draft=article("找到草稿书包",ArticleStatus.DRAFT,202);
        assertNotNull(publicItems.detail(visible));
        try {
            String response=new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .writeValueAsString(publicItems.detail(visible));
            assertFalse(response.contains("contactInfo"));
            assertFalse(response.contains("私密联系方式"));
            assertTrue(response.contains("物品 / 书包"));
        } catch(Exception error) { throw new AssertionError(error); }
        try { publicItems.detail(draft); fail(); } catch(com.team.cms.common.exception.BusinessException expected) { assertEquals(404,expected.getStatus().value()); }
        PublicArticleQuery query=new PublicArticleQuery();query.setKeyword("蓝色");query.setLocation("图书馆");
        query.setItemType(ItemType.FOUND);query.setCategoryId(201L);
        query.setFrom(LocalDateTime.now().minusDays(2));query.setTo(LocalDateTime.now());
        assertEquals(1,publicItems.list(query).getTotal());
        jdbc().update("UPDATE cms_category SET enabled=0 WHERE id=201");
        assertEquals(0,publicItems.list(query).getTotal());
        try { publicItems.detail(visible); fail(); } catch(com.team.cms.common.exception.BusinessException expected) { assertEquals(404,expected.getStatus().value()); }
    }
    @Test public void batchFailureRollsBackEarlierRow() {
        long approved=article("已审核书包",ArticleStatus.APPROVED,202);
        long draft=article("未审核书包",ArticleStatus.DRAFT,202);
        CurrentUserContext.set(new CurrentUser(99L,"admin","管理员",Set.of("ADMIN"),Set.of()));
        try {
            workflow.batch("publish",Map.of(approved,0,draft,0));fail();
        } catch(com.team.cms.common.exception.BusinessException expected) {
            assertEquals(409,expected.getStatus().value());
        } finally { CurrentUserContext.clear(); }
        assertEquals(ArticleStatus.APPROVED,articles.selectById(approved).getStatus());
        assertEquals(ArticleStatus.DRAFT,articles.selectById(draft).getStatus());
    }
    @Test public void batchRequiresPermissionAndFreshVersion() {
        long approved=article("可发布物品",ArticleStatus.APPROVED,202);
        CurrentUserContext.set(new CurrentUser(7L,"student","学生",Set.of("EDITOR"),Set.of()));
        try { workflow.batch("publish",Map.of(approved,0));fail(); }
        catch(com.team.cms.common.exception.BusinessException expected) { assertEquals(403,expected.getStatus().value()); }
        finally { CurrentUserContext.clear(); }
        CurrentUserContext.set(new CurrentUser(99L,"admin","管理员",Set.of("ADMIN"),Set.of()));
        try { workflow.batch("publish",Map.of(approved,1));fail(); }
        catch(com.team.cms.common.exception.BusinessException expected) { assertEquals(409,expected.getStatus().value()); }
        finally { CurrentUserContext.clear(); }
        assertEquals(ArticleStatus.APPROVED,articles.selectById(approved).getStatus());
    }
    @Test public void batchPublishOfflineDeleteUsesVersionsAndLogicalDelete() {
        long first=article("第一件物品",ArticleStatus.APPROVED,202);
        long second=article("第二件物品",ArticleStatus.APPROVED,202);
        CurrentUserContext.set(new CurrentUser(99L,"admin","管理员",Set.of("ADMIN"),Set.of()));
        try {
            workflow.batch("publish",Map.of(first,0,second,0));
            assertEquals(ArticleStatus.PUBLISHED,articles.selectById(first).getStatus());
            workflow.batch("offline",Map.of(first,1,second,1));
            assertEquals(ArticleStatus.OFFLINE,articles.selectById(first).getStatus());
            workflow.batch("delete",Map.of(first,2,second,2));
            assertNull(articles.selectById(first)); assertNull(articles.selectById(second));
        } finally { CurrentUserContext.clear(); }
    }
}
