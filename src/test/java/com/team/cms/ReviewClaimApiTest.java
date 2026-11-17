package com.team.cms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cms.article.mapper.ArticleMapper;
import com.team.cms.audit.entity.OperationLog;
import com.team.cms.audit.mapper.OperationLogMapper;
import com.team.cms.auth.AuthConstants;
import com.team.cms.auth.security.CurrentUser;
import com.team.cms.claim.mapper.ClaimRequestMapper;
import com.team.cms.common.domain.ArticleStatus;
import com.team.cms.common.domain.ResolutionStatus;
import com.team.cms.common.web.RequestLogFilter;
import com.team.cms.config.SpringConfig;
import com.team.cms.config.SpringMvcConfig;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.CharacterEncodingFilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 杨程模块的 HTTP 契约验收：401/403、审核/认领/交接主流程、审计与联系方式隐私。 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextHierarchy({
    @ContextConfiguration(name = "root", classes = SpringConfig.class),
    @ContextConfiguration(name = "mvc", classes = SpringMvcConfig.class)
})
@TestPropertySource(properties = {
    "jdbc.driver=org.h2.Driver",
    "jdbc.url=jdbc:h2:mem:reviewclaimapi;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "jdbc.username=sa",
    "jdbc.password="
})
@Sql(scripts = "/review-claim-schema.sql", config = @SqlConfig(encoding = "UTF-8"))
public class ReviewClaimApiTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ArticleMapper articleMapper;
    @Autowired private ClaimRequestMapper claimRequestMapper;
    @Autowired private OperationLogMapper operationLogMapper;

    private static final String CSRF_TOKEN = "test-csrf-token";

    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Before
    public void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(new CharacterEncodingFilter("UTF-8", true), new RequestLogFilter())
            .defaultRequest(post("/").header(AuthConstants.CSRF_HEADER, CSRF_TOKEN))
            .build();
    }

    @Test
    public void reviewQueueRequiresLoginAndReviewPermission() throws Exception {
        assertEquals(401, mvc.perform(get("/api/reviews")).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(get("/api/reviews").session(session(student(4L))))
            .andReturn().getResponse().getStatus());

        MvcResult result = mvc.perform(get("/api/reviews").session(session(reviewer())))
            .andReturn();
        assertEquals(200, result.getResponse().getStatus());
        JsonNode data = body(result).get("data");
        assertEquals(1L, data.get("total").asLong());
        assertEquals(4L, data.get("records").get(0).get("id").asLong());
        assertFalse("待办视图不应包含正文", data.get("records").get(0).has("content"));
    }

    @Test
    public void reviewerCannotReviewOwnItemAndFailureIsAudited() throws Exception {
        String payload = "{\"version\":1,\"decision\":\"APPROVED\",\"comment\":\"自审\"}";
        MvcResult result = mvc.perform(post("/api/reviews/5/decision")
            .session(session(reviewer())).contentType(MediaType.APPLICATION_JSON).content(payload))
            .andReturn();

        assertEquals(403, result.getResponse().getStatus());
        List<OperationLog> logs = operationLogMapper.selectList(null);
        assertTrue(logs.stream().anyMatch(log -> "REVIEW_DECIDE".equals(log.getAction())
            && "FAILURE".equals(log.getResult()) && Long.valueOf(5L).equals(log.getTargetId())));
    }

    @Test
    public void rejectionWritesReviewRecordAndSuccessAuditWithRequestId() throws Exception {
        String payload = "{\"version\":1,\"decision\":\"REJECTED\",\"comment\":\"请补充物品特征\"}";
        MvcResult result = mvc.perform(post("/api/reviews/4/decision")
            .session(session(reviewer())).contentType(MediaType.APPLICATION_JSON).content(payload))
            .andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(ArticleStatus.REJECTED, articleMapper.selectById(4L).getStatus());

        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertNotNull(requestId);
        List<OperationLog> logs = operationLogMapper.selectList(null);
        assertTrue(logs.stream().anyMatch(log -> "REVIEW_DECIDE".equals(log.getAction())
            && "SUCCESS".equals(log.getResult())
            && Long.valueOf(4L).equals(log.getTargetId())
            && requestId.equals(log.getRequestId())
            && Long.valueOf(3L).equals(log.getOperatorId())));
    }

    @Test
    public void claimApplyApproveContactAndHandoverFlowOverHttp() throws Exception {
        String applyBody = json.writeValueAsString(java.util.Map.of(
            "claimDescription", "我丢失的雨伞与描述完全一致，可提供购买记录和照片。",
            "contactInfo", "13900000002"));
        MvcResult applied = mvc.perform(post("/api/items/1/claims")
            .session(session(student(2L))).contentType(MediaType.APPLICATION_JSON).content(applyBody))
            .andReturn();
        assertEquals(200, applied.getResponse().getStatus());
        JsonNode claimData = body(applied).get("data");
        long claimId = claimData.get("id").asLong();
        assertEquals("PENDING", claimData.get("status").asText());

        MvcResult mine = mvc.perform(get("/api/claims/mine").session(session(student(2L))))
            .andReturn();
        assertEquals(1L, body(mine).get("data").get("total").asLong());

        String approveBody = "{\"version\":0,\"decision\":\"APPROVED\",\"comment\":\"特征吻合\"}";
        MvcResult approved = mvc.perform(post("/api/claims/" + claimId + "/decision")
            .session(session(reviewer())).contentType(MediaType.APPLICATION_JSON).content(approveBody))
            .andReturn();
        assertEquals(200, approved.getResponse().getStatus());
        assertEquals("APPROVED", claimRequestMapper.selectById(claimId).getStatus());
        assertEquals(ResolutionStatus.OPEN, articleMapper.selectById(1L).getResolutionStatus());

        MvcResult contact = mvc.perform(get("/api/items/1/found-contact")
            .session(session(student(2L)))).andReturn();
        assertEquals(200, contact.getResponse().getStatus());
        assertEquals("13800000001", body(contact).get("data").get("contactInfo").asText());

        String resolveBody = "{\"version\":2,\"claimId\":" + claimId + "}";
        MvcResult resolved = mvc.perform(post("/api/items/1/resolve")
            .session(session(student(1L))).contentType(MediaType.APPLICATION_JSON).content(resolveBody))
            .andReturn();
        assertEquals(200, resolved.getResponse().getStatus());
        assertEquals("COMPLETED", claimRequestMapper.selectById(claimId).getStatus());
        assertEquals(ResolutionStatus.RESOLVED,
            articleMapper.selectById(1L).getResolutionStatus());
        assertNotNull(articleMapper.selectById(1L).getResolvedAt());
    }

    @Test
    public void unrelatedStudentCannotReadFoundContactAndFailureIsAudited() throws Exception {
        MvcResult result = mvc.perform(get("/api/items/1/found-contact")
            .session(session(student(4L)))).andReturn();

        assertEquals(403, result.getResponse().getStatus());
        List<OperationLog> logs = operationLogMapper.selectList(null);
        assertTrue(logs.stream().anyMatch(log -> "FOUND_CONTACT_READ".equals(log.getAction())
            && "FAILURE".equals(log.getResult()) && Long.valueOf(1L).equals(log.getTargetId())));
    }

    @Test
    public void operationLogsAndClaimQueueRejectStudentsButAllowAdmins() throws Exception {
        assertEquals(403, mvc.perform(get("/api/claims").session(session(student(4L))))
            .andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(get("/api/operation-logs").session(session(student(4L))))
            .andReturn().getResponse().getStatus());

        assertEquals(200, mvc.perform(get("/api/operation-logs").session(session(admin())))
            .andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(get("/api/claims").session(session(reviewer())))
            .andReturn().getResponse().getStatus());
    }

    @Test
    public void handoverContextExposesVersionAndApprovedClaimOnlyToOwner() throws Exception {
        String applyBody = json.writeValueAsString(java.util.Map.of(
            "claimDescription", "我丢失的雨伞与描述完全一致，可提供购买记录和照片。",
            "contactInfo", "13900000002"));
        MvcResult applied = mvc.perform(post("/api/items/1/claims")
            .session(session(student(2L))).contentType(MediaType.APPLICATION_JSON).content(applyBody))
            .andReturn();
        long claimId = body(applied).get("data").get("id").asLong();
        String approveBody = "{\"version\":0,\"decision\":\"APPROVED\",\"comment\":\"特征吻合\"}";
        mvc.perform(post("/api/claims/" + claimId + "/decision")
            .session(session(reviewer())).contentType(MediaType.APPLICATION_JSON).content(approveBody))
            .andReturn();

        MvcResult owner = mvc.perform(get("/api/items/1/handover")
            .session(session(student(1L)))).andReturn();
        assertEquals(200, owner.getResponse().getStatus());
        JsonNode data = body(owner).get("data");
        assertEquals(2, data.get("version").asInt());
        assertEquals(claimId, data.get("approvedClaimId").asLong());
        assertFalse(data.has("contactInfo"));

        assertEquals(403, mvc.perform(get("/api/items/1/handover")
            .session(session(student(4L)))).andReturn().getResponse().getStatus());
    }

    @Test
    public void reviewRecordsAreVisibleToAuthorButNotUnrelatedStudent() throws Exception {
        assertEquals(200, mvc.perform(get("/api/items/4/review-records")
            .session(session(student(1L)))).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(get("/api/items/4/review-records")
            .session(session(student(4L)))).andReturn().getResponse().getStatus());
    }

    @Test
    public void modulePagesAreServedAsStaticResources() throws Exception {
        MvcResult reviewPage = mvc.perform(get("/review/review.html")).andReturn();
        assertEquals(200, reviewPage.getResponse().getStatus());
        assertTrue(reviewPage.getResponse().getContentAsString().contains("审核工作台"));
        assertEquals(200, mvc.perform(get("/claim/manage.html")).andReturn()
            .getResponse().getStatus());
        assertEquals(200, mvc.perform(get("/claim/handover.html")).andReturn()
            .getResponse().getStatus());
        assertEquals(200, mvc.perform(get("/claim/mine.html")).andReturn()
            .getResponse().getStatus());
        assertEquals(200, mvc.perform(get("/audit/logs.html")).andReturn()
            .getResponse().getStatus());
        assertEquals(200, mvc.perform(get("/yangcheng/common.js")).andReturn()
            .getResponse().getStatus());
    }

    @Test
    public void emptyOptionalFiltersAreAccepted() throws Exception {
        assertEquals(200, mvc.perform(get("/api/reviews").param("itemType", "")
            .session(session(reviewer()))).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(get("/api/claims").param("status", "")
            .session(session(reviewer()))).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(get("/api/operation-logs").param("result", "")
            .session(session(admin()))).andReturn().getResponse().getStatus());
    }

    @Test
    public void invalidPayloadAndInvertedLogRangeReturn400() throws Exception {
        String shortClaim = "{\"claimDescription\":\"太短\",\"contactInfo\":\"1\"}";
        assertEquals(400, mvc.perform(post("/api/items/1/claims")
            .session(session(student(2L))).contentType(MediaType.APPLICATION_JSON).content(shortClaim))
            .andReturn().getResponse().getStatus());

        String badVersion = "{\"version\":-1,\"decision\":\"APPROVED\",\"comment\":\"\"}";
        assertEquals(400, mvc.perform(post("/api/reviews/4/decision")
            .session(session(reviewer())).contentType(MediaType.APPLICATION_JSON).content(badVersion))
            .andReturn().getResponse().getStatus());

        assertEquals(400, mvc.perform(get("/api/operation-logs")
            .session(session(admin()))
            .param("from", "2026-10-22T12:00:00")
            .param("to", "2026-10-22T10:00:00"))
            .andReturn().getResponse().getStatus());
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static MockHttpSession session(CurrentUser user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthConstants.SESSION_USER, user);
        session.setAttribute(AuthConstants.SESSION_CSRF_TOKEN, CSRF_TOKEN);
        return session;
    }

    private static CurrentUser student(long id) {
        Set<String> permissions = new LinkedHashSet<>(Arrays.asList(
            "claim:create", "claim:cancel:own", "claim:handover:own"));
        return new CurrentUser(id, "student" + id, "学生" + id,
            Collections.emptySet(), permissions);
    }

    private static CurrentUser reviewer() {
        Set<String> permissions = new LinkedHashSet<>(Arrays.asList(
            "review:handle", "claim:handle", "article:publish"));
        return new CurrentUser(3L, "reviewer01", "王审核",
            Collections.emptySet(), permissions);
    }

    private static CurrentUser admin() {
        return new CurrentUser(5L, "admin01", "系统管理员",
            Collections.singleton("ADMIN"), Collections.emptySet());
    }
}
