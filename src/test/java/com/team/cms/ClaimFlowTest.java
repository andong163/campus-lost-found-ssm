package com.team.cms;

import com.team.cms.claim.domain.ClaimDecision;
import com.team.cms.claim.dto.ClaimCancelRequest;
import com.team.cms.claim.dto.ClaimCreateRequest;
import com.team.cms.claim.dto.ClaimDecisionRequest;
import com.team.cms.claim.dto.ClaimQuery;
import com.team.cms.claim.dto.ClaimView;
import com.team.cms.claim.dto.FoundContactView;
import com.team.cms.common.api.PageResult;
import com.team.cms.common.domain.ResolutionStatus;
import com.team.cms.support.ReviewClaimTestSupport;
import org.junit.Test;
import org.springframework.http.HttpStatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 认领申请、互斥、核实、撤销释放名额、联系方式策略的验收。 */
public class ClaimFlowTest extends ReviewClaimTestSupport {

    @Test
    public void applyCreatesPendingClaimAndMineReturnsIt() {
        asApplicant();
        ClaimView view = claimService.apply(1L, applyRequest());

        assertNotNull(view.getId());
        assertEquals("PENDING", view.getStatus());
        assertEquals("拾到黑色雨伞", view.getArticleTitle());
        assertEquals("李同学", view.getApplicantName());

        PageResult<ClaimView> mine = claimService.mine(new ClaimQuery());
        assertEquals(1L, mine.getTotal());
        assertEquals(view.getId(), mine.getRecords().get(0).getId());
    }

    @Test
    public void applyRejectsOwnItemLostItemUnpublishedItemAndResolvedItem() {
        asAuthor();
        assertStatus(HttpStatus.CONFLICT, () -> claimService.apply(1L, applyRequest()));

        asApplicant();
        assertStatus(HttpStatus.CONFLICT, () -> claimService.apply(2L, applyRequest()));
        assertStatus(HttpStatus.CONFLICT, () -> claimService.apply(3L, applyRequest()));
        assertStatus(HttpStatus.CONFLICT, () -> claimService.apply(8L, applyRequest()));
    }

    @Test
    public void newClaimUnderDisabledCategoryIsRejectedEvenIfItemIsPublished() {
        asApplicant();
        assertStatus(HttpStatus.CONFLICT, () -> claimService.apply(7L, applyRequest()));
        assertEquals(0L, claimService.mine(new ClaimQuery()).getTotal());
    }

    @Test
    public void duplicateActiveApplicationRejectedUntilCancelled() {
        asApplicant();
        ClaimView first = claimService.apply(1L, applyRequest());
        assertStatus(HttpStatus.CONFLICT, () -> claimService.apply(1L, applyRequest()));

        ClaimCancelRequest cancel = new ClaimCancelRequest();
        cancel.setVersion(0);
        cancel.setReason("暂时无法提供更多证据");
        claimService.cancel(first.getId(), cancel);
        assertEquals("CANCELLED", claim(first.getId()).getStatus());

        ClaimView second = claimService.apply(1L, applyRequest());
        assertEquals("PENDING", second.getStatus());
        assertFalse(first.getId().equals(second.getId()));
    }

    @Test
    public void approvingClaimDoesNotResolveItemAndOnlyOneApprovalAllowed() {
        asApplicant();
        ClaimView first = claimService.apply(1L, applyRequest());
        asOtherStudent();
        ClaimView second = claimService.apply(1L, applyRequest());

        asReviewer();
        claimService.decide(first.getId(), decision(ClaimDecision.APPROVED, 0, "特征吻合"));
        assertEquals("APPROVED", claim(first.getId()).getStatus());
        assertEquals(ResolutionStatus.OPEN, article(1L).getResolutionStatus());
        assertEquals(com.team.cms.common.domain.ArticleStatus.PUBLISHED, article(1L).getStatus());

        assertStatus(HttpStatus.CONFLICT,
            () -> claimService.decide(second.getId(),
                decision(ClaimDecision.APPROVED, 0, "另一个也通过")));
        assertEquals("PENDING", claim(second.getId()).getStatus());
    }

    @Test
    public void reviewerCannotApproveOwnApplicationOrOwnPublishedItem() {
        givenClaim(6L, 3L, "PENDING");
        asReviewer();
        assertStatus(HttpStatus.FORBIDDEN,
            () -> claimService.decide(claimRequestMapper.selectList(null).get(0).getId(),
                decision(ClaimDecision.APPROVED, 0, "本人申请")));

        asApplicant();
        ClaimView claim = claimService.apply(6L, applyRequest());
        asReviewer();
        assertStatus(HttpStatus.FORBIDDEN,
            () -> claimService.decide(claim.getId(),
                decision(ClaimDecision.APPROVED, 0, "本人发布物品")));
    }

    @Test
    public void rejectionRequiresReasonAndRecordsReviewer() {
        asApplicant();
        ClaimView claim = claimService.apply(1L, applyRequest());
        asReviewer();

        assertStatus(HttpStatus.BAD_REQUEST,
            () -> claimService.decide(claim.getId(), decision(ClaimDecision.REJECTED, 0, " ")));

        claimService.decide(claim.getId(), decision(ClaimDecision.REJECTED, 0, "特征不符"));
        assertEquals("REJECTED", claim(claim.getId()).getStatus());
        assertEquals("特征不符", claim(claim.getId()).getReviewComment());
        assertEquals(Long.valueOf(3L), claim(claim.getId()).getReviewerId());
        assertNotNull(claim(claim.getId()).getReviewedAt());
    }

    @Test
    public void cancellingApprovedClaimReleasesApprovalSlot() {
        asApplicant();
        ClaimView approved = claimService.apply(1L, applyRequest());
        asReviewer();
        claimService.decide(approved.getId(), decision(ClaimDecision.APPROVED, 0, "通过"));

        asApplicant();
        ClaimCancelRequest cancel = new ClaimCancelRequest();
        cancel.setVersion(1);
        cancel.setReason("已与发布者线下沟通放弃");
        claimService.cancel(approved.getId(), cancel);
        assertEquals("CANCELLED", claim(approved.getId()).getStatus());

        asOtherStudent();
        ClaimView replacement = claimService.apply(1L, applyRequest());
        assertEquals("PENDING", replacement.getStatus());
    }

    @Test
    public void foundContactPolicyAllowsOwnerStaffAndApprovedApplicantOnly() {
        asApplicant();
        assertFalse(claimReadPolicy.canReadFoundContact(article(1L),
            com.team.cms.auth.security.CurrentUserContext.get()));
        assertStatus(HttpStatus.FORBIDDEN, () -> claimReadPolicy.readFoundContact(1L));

        ClaimView claim = claimService.apply(1L, applyRequest());
        asReviewer();
        claimService.decide(claim.getId(), decision(ClaimDecision.APPROVED, 0, "通过"));

        asApplicant();
        assertTrue(claimReadPolicy.canReadFoundContact(article(1L),
            com.team.cms.auth.security.CurrentUserContext.get()));
        FoundContactView contact = claimReadPolicy.readFoundContact(1L);
        assertEquals("13800000001", contact.getContactInfo());

        asAuthor();
        assertTrue(claimReadPolicy.canReadFoundContact(article(1L),
            com.team.cms.auth.security.CurrentUserContext.get()));

        asOtherStudent();
        assertStatus(HttpStatus.FORBIDDEN, () -> claimReadPolicy.readFoundContact(1L));
        assertStatus(HttpStatus.BAD_REQUEST, () -> claimReadPolicy.readFoundContact(2L));
    }

    @Test
    public void forItemRequiresOwnerOrClaimHandlerAndMineIsPrivateToApplicant() {
        asApplicant();
        ClaimView claim = claimService.apply(1L, applyRequest());

        asOtherStudent();
        assertStatus(HttpStatus.FORBIDDEN, () -> claimService.forItem(1L));
        assertEquals(0L, claimService.mine(new ClaimQuery()).getTotal());

        asAuthor();
        assertEquals(1, claimService.forItem(1L).size());
        assertEquals(claim.getId(), claimService.forItem(1L).get(0).getId());

        asReviewer();
        assertEquals(1L, claimService.queue(new ClaimQuery()).getTotal());
    }

    private static ClaimCreateRequest applyRequest() {
        ClaimCreateRequest request = new ClaimCreateRequest();
        request.setClaimDescription("我丢失的物品与描述一致，可提供购买记录和实物照片。");
        request.setContactInfo("13900000002");
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
