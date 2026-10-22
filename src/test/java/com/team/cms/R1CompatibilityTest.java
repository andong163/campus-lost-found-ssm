package com.team.cms;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cms.article.entity.Article;
import com.team.cms.article.mapper.ArticleMapper;
import com.team.cms.audit.service.AuditTemplate;
import com.team.cms.auth.AuthConstants;
import com.team.cms.auth.security.CurrentUser;
import com.team.cms.auth.service.AuthService;
import com.team.cms.claim.entity.ClaimRequest;
import com.team.cms.claim.mapper.ClaimRequestMapper;
import com.team.cms.common.domain.ArticleStatus;
import com.team.cms.common.domain.ResolutionStatus;
import com.team.cms.common.web.RequestLogFilter;
import com.team.cms.config.*;
import com.team.cms.review.dto.ReviewHistoryQuery;
import com.team.cms.review.entity.ReviewRecord;
import com.team.cms.review.mapper.ReviewRecordMapper;
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
import org.springframework.test.context.*;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Tests the merged R1 boundaries, not a replacement R2 workflow or R3 claim service. */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextHierarchy({@ContextConfiguration(name="root", classes=SpringConfig.class),
    @ContextConfiguration(name="mvc", classes=SpringMvcConfig.class)})
@TestPropertySource(properties={"jdbc.driver=org.h2.Driver", "jdbc.url=jdbc:h2:mem:r1_total;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "jdbc.username=sa", "jdbc.password="})
@Sql({"/review-claim-schema.sql", "/auth-schema-test.sql"})
public class R1CompatibilityTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ArticleMapper articles;
    @Autowired private ClaimRequestMapper claims;
    @Autowired private ReviewRecordMapper reviews;
    @Autowired private AuthService auth;
    @Autowired private AuditTemplate audit;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource source;
    private MockMvc mvc;
    private MockHttpSession admin;
    private final ObjectMapper json = new ObjectMapper();
    private JdbcTemplate sql() { return new JdbcTemplate(source); }
    private TransactionTemplate transactions() { return new TransactionTemplate(transactionManager); }

    @Before public void setup() throws Exception {
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(new RequestLogFilter()).build();
        auth.ensureAdmin("r1admin", "r1-test-password");
        admin=login("r1admin");
    }
    private MockHttpSession login(String username) throws Exception {
        MvcResult result=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username",username,"password","r1-test-password")))).andReturn();
        assertEquals(200,result.getResponse().getStatus());
        return (MockHttpSession)result.getRequest().getSession(false);
    }
    private MockHttpSession student() throws Exception {
        assertEquals(200,mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username","r1student","displayName","学生测试","password","r1-test-password"))))
            .andReturn().getResponse().getStatus());
        return login("r1student");
    }

    @Test public void bothAuthorsMapperCommandsShareEnumsVersionsAndOneLockQuery() {
        transactions().execute(status -> {
            Article draft=articles.selectByIdForUpdate(3L);
            assertTrue(draft.isFound()); assertFalse(draft.isLost());
            assertEquals(ArticleStatus.DRAFT,draft.getStatus());
            assertNotNull(articles.selectDraftByIdAndAuthor(3,1));
            assertEquals(1,articles.updateStatusIfVersion(3,ArticleStatus.DRAFT,ArticleStatus.PENDING_REVIEW,0));
            assertEquals(0,articles.updateStatus(3L,"DRAFT","APPROVED",0,null));
            assertEquals(1,articles.updateStatus(3L,"PENDING_REVIEW","APPROVED",1,null));
            LocalDateTime published=LocalDateTime.of(2026,10,20,12,0);
            assertEquals(1,articles.updateStatus(3L,"APPROVED","PUBLISHED",2,published));
            assertEquals(published,articles.selectById(3L).getPublishedAt());
            assertEquals(0,articles.resolve(3L,2,published.plusMinutes(1)));
            assertEquals(1,articles.resolve(3L,3,published.plusMinutes(1)));
            assertEquals(ResolutionStatus.RESOLVED,articles.selectById(3L).getResolutionStatus());
            assertEquals(0,articles.resolve(3L,4,published.plusMinutes(2)));
            articles.deleteById(3L);
            assertNull(articles.selectByIdForUpdate(3L));
            assertEquals(0,articles.updateStatus(3L,"PUBLISHED","OFFLINE",4,null));
            assertEquals(0,articles.resolve(3L,4,published));
            return null;
        });
    }

    @Test public void auditHttpUsesRealIdentityPermissionValidationAndSafeProjection() throws Exception {
        audit.run("foundation","R1_PROBE",3L,()->{});
        assertEquals(401,mvc.perform(get("/api/operation-logs")).andReturn().getResponse().getStatus());
        MockHttpSession student=student();
        assertEquals(403,mvc.perform(get("/api/operation-logs").session(student)).andReturn().getResponse().getStatus());
        MvcResult visible=mvc.perform(get("/api/operation-logs").session(admin).param("action","R1_PROBE")).andReturn();
        assertEquals(200,visible.getResponse().getStatus());
        JsonNode data=json.readTree(visible.getResponse().getContentAsByteArray()).get("data");
        assertEquals(1,data.get("total").asInt());
        JsonNode record=data.get("records").get(0);
        assertFalse(record.get("requestId").asText().isBlank());
        for(String field:new String[]{"password","passwordHash","content","contactInfo","claimDescription"}) assertFalse(record.has(field));
        assertEquals(400,mvc.perform(get("/api/operation-logs").session(admin).param("page","0")).andReturn().getResponse().getStatus());
        assertEquals(400,mvc.perform(get("/api/operation-logs").session(admin).param("from","2026-10-22T12:00:00")
            .param("to","2026-10-21T12:00:00")).andReturn().getResponse().getStatus());
        long id=((CurrentUser)student.getAttribute(AuthConstants.SESSION_USER)).getId();
        sql().update("DELETE FROM sys_user_role WHERE user_id=?",id);
        sql().update("INSERT INTO sys_user_role SELECT ?,id FROM sys_role WHERE code='VIEWER'",id);
        sql().update("INSERT INTO sys_role_permission SELECT r.id,p.id FROM sys_role r,sys_permission p WHERE r.code='VIEWER' AND p.code='audit:view'");
        assertEquals(200,mvc.perform(get("/api/operation-logs").session(login("r1student"))).andReturn().getResponse().getStatus());
    }

    @Test public void businessRollbackSurvivesAuditAndIsRecordedAsFailure() {
        try {
            audit.execute("foundation","R1_ROLLBACK",3L,()->transactions().execute(status->{
                assertEquals(1,articles.updateStatusIfVersion(3,ArticleStatus.DRAFT,ArticleStatus.PENDING_REVIEW,0));
                throw new IllegalStateException("controlled-r1-failure");
            }));
            fail("Expected the original business failure");
        } catch(IllegalStateException expected) { assertEquals("controlled-r1-failure",expected.getMessage()); }
        assertEquals(ArticleStatus.DRAFT,articles.selectById(3L).getStatus());
        assertEquals(Integer.valueOf(0),articles.selectById(3L).getVersion());
        assertEquals("FAILURE",sql().queryForObject("SELECT result FROM sys_operation_log WHERE action='R1_ROLLBACK'",String.class));
    }

    @Test public void auditDatabaseFailureDoesNotUndoSuccessfulCommittedBusiness() {
        sql().execute("DROP TABLE sys_operation_log");
        String result=audit.execute("foundation","R1_AUDIT_FAILURE",3L,()->transactions().execute(status->{
            assertEquals(1,articles.updateStatusIfVersion(3,ArticleStatus.DRAFT,ArticleStatus.PENDING_REVIEW,0));
            return "business-committed";
        }));
        assertEquals("business-committed",result);
        assertEquals(ArticleStatus.PENDING_REVIEW,articles.selectById(3L).getStatus());
        assertEquals(Integer.valueOf(1),articles.selectById(3L).getVersion());
    }

    @Test public void reviewAndClaimReadCloseContractsRemainExecutable() {
        long operator=((CurrentUser)admin.getAttribute(AuthConstants.SESSION_USER)).getId();
        transactions().execute(status->{
            ReviewRecord review=new ReviewRecord(); review.setArticleId(3L); review.setReviewerId(operator);
            review.setDecision("APPROVED"); review.setComment("R1 mapper probe"); review.setArticleVersion(0);
            review.setCreatedAt(LocalDateTime.now());
            assertEquals(1,reviews.insert(review));
            assertEquals(1,reviews.selectViewsByArticleId(3L).size());
            ReviewHistoryQuery query=new ReviewHistoryQuery(); query.setArticleId(3L);
            assertEquals(1,reviews.selectHistory(new Page<>(1,10),query).getTotal());
            ClaimRequest request=new ClaimRequest(); request.setArticleId(3L); request.setApplicantId(operator);
            request.setClaimDescription("test-only description"); request.setContactInfo("test-only contact");
            request.setStatus("PENDING"); request.setVersion(0);
            assertEquals(1,claims.insert(request));
            assertEquals(1,claims.countActiveByApplicant(3L,operator));
            assertEquals(1,claims.countActiveByItem(3L));
            assertEquals(0,claims.countApprovedByItem(3L));
            assertNotNull(claims.selectByIdForUpdate(request.getId()));
            assertEquals(1,claims.closePendingByItem(3L,"R1 close probe"));
            assertEquals(0,claims.countActiveByApplicant(3L,operator));
            assertEquals(0,claims.closePendingByItem(3L,"repeat"));
            assertEquals("CANCELLED",claims.selectById(request.getId()).getStatus());
            assertEquals(1,claims.selectMine(new Page<>(1,10),operator,null).getTotal());
            return null;
        });
    }
}
