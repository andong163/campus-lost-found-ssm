package com.team.cms;

import com.team.cms.common.domain.ArticleStatus;
import com.team.cms.common.domain.ResolutionStatus;
import com.team.cms.review.domain.ReviewDecision;
import com.team.cms.review.dto.ReviewDecisionRequest;
import com.team.cms.support.ReviewTestSupport;
import org.junit.Test;
import org.springframework.http.HttpStatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 第二轮验收：内容提交、审核、发布、下架、结案及删除守卫。 */
public class ReviewWorkflowTest extends ReviewTestSupport {

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
    public void lostAuthorConfirmsFoundAndTimestampIsWritten() {
        asAuthor();
        itemWorkflowService.resolve(2L, article(2L).getVersion(), null);

        assertEquals(ResolutionStatus.RESOLVED, article(2L).getResolutionStatus());
        assertNotNull(article(2L).getResolvedAt());
    }

    @Test
    public void nonAuthorCannotResolveLostItem() {
        asApplicant();
        assertStatus(HttpStatus.FORBIDDEN,
            () -> itemWorkflowService.resolve(2L, article(2L).getVersion(), null));
    }

    @Test
    public void deleteGuardAllowsDraftWithoutClaimsButRejectsPendingReview() {
        asAuthor();
        itemWorkflowService.assertDeletable(3L);
        assertStatus(HttpStatus.CONFLICT, () -> itemWorkflowService.assertDeletable(4L));
    }

    private static ReviewDecisionRequest decision(ReviewDecision decision, int version, String comment) {
        ReviewDecisionRequest request = new ReviewDecisionRequest();
        request.setDecision(decision);
        request.setVersion(version);
        request.setComment(comment);
        return request;
    }
}
