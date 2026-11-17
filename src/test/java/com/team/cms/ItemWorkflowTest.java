package com.team.cms;

import com.team.cms.claim.dto.ClaimCreateRequest;
import com.team.cms.claim.dto.ClaimDecisionRequest;
import com.team.cms.claim.dto.ClaimView;
import com.team.cms.claim.domain.ClaimDecision;
import com.team.cms.common.domain.ArticleStatus;
import com.team.cms.common.domain.ResolutionStatus;
import com.team.cms.review.domain.ReviewDecision;
import com.team.cms.review.dto.ReviewDecisionRequest;
import com.team.cms.support.ReviewClaimTestSupport;
import org.junit.Test;
import org.springframework.http.HttpStatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 内容状态机、审核记录同事务、认领对状态操作的阻断、交接结案的服务级验收。 */
public class ItemWorkflowTest extends ReviewClaimTestSupport {

    @Test
    public void submitAndWithdrawDraft() {
        asAuthor();
        itemWorkflowService.submit(3L, article(3L).getVersion());

        assertEquals(ArticleStatus.PENDING_REVIEW, article(3L).getStatus());
        assertEquals(Integer.valueOf(1), article(3L).getVersion());
        assertEquals(0L, reviewRecordMapper.selectCount(null).longValue());

        itemWorkflowService.withdraw(3L, article(3L).getVersion());
        assertEquals(ArticleStatus.DRAFT, article(3L).getStatus());
    }

    @Test
    public void categoryAccessRejectsDisabledAncestorsAndMissingCategories() {
        assertTrue(categoryAccessService.isBindable(2L));
        assertFalse(categoryAccessService.isBindable(3L));
        assertFalse(categoryAccessService.isBindable(4L));
        assertFalse(categoryAccessService.isBindable(99L));
        assertFalse(categoryAccessService.isBindable(null));
    }

    @Test
    public void submitRejectsDisabledCategory() {
        asAuthor();
        assertStatus(HttpStatus.CONFLICT,
            () -> itemWorkflowService.submit(9L, article(9L).getVersion()));
        assertEquals(ArticleStatus.DRAFT, article(9L).getStatus());
    }

    @Test
    public void reviewRejectionRequiresCommentAndWritesRecordInSameTransaction() {
        asReviewer();
        ReviewDecisionRequest invalid = decision(ReviewDecision.REJECTED, 1, " ");
        assertStatus(HttpStatus.BAD_REQUEST, () -> reviewService.decide(4L, invalid));
        assertEquals(ArticleStatus.PENDING_REVIEW, article(4L).getStatus());
        assertEquals(0L, reviewRecordMapper.selectCount(null).longValue());

        ReviewDecisionRequest valid = decision(ReviewDecision.REJECTED, 1, "请补充物品特征");
        reviewService.decide(4L, valid);

        assertEquals(ArticleStatus.REJECTED, article(4L).getStatus());
        assertEquals(1L, reviewRecordMapper.selectCount(null).longValue());
        assertEquals("REJECTED", reviewRecordMapper.selectList(null).get(0).getDecision());
        assertEquals(Integer.valueOf(1), reviewRecordMapper.selectList(null).get(0).getArticleVersion());
    }

    @Test
    public void reviewerCannotReviewOwnContentAndStaleVersionIsRejected() {
        asReviewer();
        assertStatus(HttpStatus.FORBIDDEN,
            () -> reviewService.decide(5L, decision(ReviewDecision.APPROVED, 1, "")));
        assertStatus(HttpStatus.CONFLICT,
            () -> reviewService.decide(4L, decision(ReviewDecision.APPROVED, 0, "")));
    }

    @Test
    public void approvedItemCanBePublishedAndOfflined() {
        asReviewer();
        reviewService.decide(4L, decision(ReviewDecision.APPROVED, 1, "内容完整"));
        assertEquals(ArticleStatus.APPROVED, article(4L).getStatus());

        itemWorkflowService.publish(4L, article(4L).getVersion());
        assertEquals(ArticleStatus.PUBLISHED, article(4L).getStatus());
        assertNotNull(article(4L).getPublishedAt());

        itemWorkflowService.offline(4L, article(4L).getVersion(), "信息有误");
        assertEquals(ArticleStatus.OFFLINE, article(4L).getStatus());
    }

    @Test
    public void offlineClosesPendingClaims() {
        asApplicant();
        ClaimView claim = claimService.apply(1L, applyRequest());

        asAuthor();
        itemWorkflowService.offline(1L, article(1L).getVersion(), "重复发布");

        assertEquals(ArticleStatus.OFFLINE, article(1L).getStatus());
        assertEquals("CANCELLED", claim(claim.getId()).getStatus());
        assertEquals("重复发布", claim(claim.getId()).getCancelReason());
    }

    @Test
    public void approvedClaimBlocksOfflineDraftAndDelete() {
        asApplicant();
        ClaimView claim = claimService.apply(1L, applyRequest());
        asReviewer();
        claimService.decide(claim.getId(), decision(ClaimDecision.APPROVED, 0, "特征吻合"));

        asAuthor();
        assertStatus(HttpStatus.CONFLICT,
            () -> itemWorkflowService.offline(1L, article(1L).getVersion(), "下架"));
        assertStatus(HttpStatus.CONFLICT, () -> itemWorkflowService.assertDeletable(1L));
        assertEquals(ArticleStatus.PUBLISHED, article(1L).getStatus());
    }

    @Test
    public void lostAuthorConfirmsFoundAndTimestampIsWritten() {
        asAuthor();
        itemWorkflowService.resolve(2L, article(2L).getVersion(), null);

        assertEquals(ResolutionStatus.RESOLVED, article(2L).getResolutionStatus());
        assertNotNull(article(2L).getResolvedAt());
    }

    @Test
    public void foundHandoverCompletesApprovedClaimAndClosesOtherPendingClaims() {
        asApplicant();
        ClaimView approved = claimService.apply(1L, applyRequest());
        asOtherStudent();
        ClaimView pending = claimService.apply(1L, applyRequest());
        asReviewer();
        claimService.decide(approved.getId(), decision(ClaimDecision.APPROVED, 0, "特征吻合"));

        asAuthor();
        itemWorkflowService.resolve(1L, article(1L).getVersion(), approved.getId());

        assertEquals("COMPLETED", claim(approved.getId()).getStatus());
        assertNotNull(claim(approved.getId()).getCompletedAt());
        assertEquals("CANCELLED", claim(pending.getId()).getStatus());
        assertEquals(ResolutionStatus.RESOLVED, article(1L).getResolutionStatus());
        assertNotNull(article(1L).getResolvedAt());

        assertStatus(HttpStatus.CONFLICT,
            () -> itemWorkflowService.resolve(1L, article(1L).getVersion(), approved.getId()));
    }

    @Test
    public void nonAuthorCannotResolveLostItem() {
        asApplicant();
        assertStatus(HttpStatus.FORBIDDEN,
            () -> itemWorkflowService.resolve(2L, article(2L).getVersion(), null));
    }

    @Test
    public void pendingClaimBlocksDeletionButNotOffline() {
        asApplicant();
        claimService.apply(1L, applyRequest());
        asAuthor();
        assertStatus(HttpStatus.CONFLICT, () -> itemWorkflowService.assertDeletable(1L));
    }

    @Test
    public void legacyClaimUnderDisabledCategoryCanStillBeVerified() {
        givenClaim(7L, 2L, "PENDING");
        asReviewer();
        claimService.decide(claimRequestMapper.selectList(null).get(0).getId(),
            decision(ClaimDecision.APPROVED, 0, "旧申请继续核实"));

        assertEquals("APPROVED", claimRequestMapper.selectList(null).get(0).getStatus());
    }

    @Test
    public void deleteGuardAllowsDraftWithoutClaimsButRejectsPendingReview() {
        asAuthor();
        itemWorkflowService.assertDeletable(3L);
        assertStatus(HttpStatus.CONFLICT, () -> itemWorkflowService.assertDeletable(4L));
    }

    private static ClaimCreateRequest applyRequest() {
        ClaimCreateRequest request = new ClaimCreateRequest();
        request.setClaimDescription("我丢失的物品与描述一致，可提供购买记录和实物照片。");
        request.setContactInfo("13900000002");
        return request;
    }

    private static ReviewDecisionRequest decision(ReviewDecision decision, int version, String comment) {
        ReviewDecisionRequest request = new ReviewDecisionRequest();
        request.setDecision(decision);
        request.setVersion(version);
        request.setComment(comment);
        return request;
    }

    private static ClaimDecisionRequest decision(ClaimDecision decision, int version, String comment) {
        ClaimDecisionRequest request = new ClaimDecisionRequest();
        request.setDecision(decision);
        request.setVersion(version);
        request.setComment(comment);
        return request;
    }
}
