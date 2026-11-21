package com.team.cms;

import com.team.cms.claim.domain.ClaimDecision;
import com.team.cms.claim.dto.ClaimCancelRequest;
import com.team.cms.claim.dto.ClaimDecisionRequest;
import com.team.cms.claim.entity.ClaimRequest;
import com.team.cms.review.domain.ReviewDecision;
import com.team.cms.review.dto.ReviewDecisionRequest;
import com.team.cms.support.ReviewClaimTestSupport;
import javax.sql.DataSource;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.Assert.assertEquals;

/** 版本按数值比较，不能依赖 Integer 的小整数缓存；使用真实服务、Mapper 和事务。 */
public class VersionComparisonRegressionTest extends ReviewClaimTestSupport {
    @Autowired private DataSource source;

    @Test public void reviewMatchingVersionOne() { approveAt(1); }
    @Test public void reviewMatchingVersion127() { approveAt(127); }
    @Test public void reviewMatchingVersion128() { approveAt(128); }

    /** 不直接改版本：正常提交和撤回累积到缓存范围之外仍能审核。 */
    @Test public void reviewAfterActualSubmitWithdrawCycles() {
        asAuthor();
        for (int i = 0; i < 64; i++) {
            itemWorkflowService.submit(3L, article(3L).getVersion());
            itemWorkflowService.withdraw(3L, article(3L).getVersion());
        }
        itemWorkflowService.submit(3L, article(3L).getVersion());
        assertEquals(Integer.valueOf(129), article(3L).getVersion());
        asReviewer();
        reviewService.decide(3L, reviewDecision(129));
        assertEquals("APPROVED", article(3L).getStatus().name());
        assertEquals(Integer.valueOf(130), article(3L).getVersion());
    }

    /** 认领正常流程版本较低；人工构造高版本，覆盖同类引用比较缺陷。 */
    @Test public void claimDecisionAcceptsMatchingHighVersion() {
        ClaimRequest claim = highVersionClaim();
        asReviewer();
        claimService.decide(claim.getId(), claimDecision(128));
        assertEquals("APPROVED", claim(claim.getId()).getStatus());
        assertEquals(Integer.valueOf(129), claim(claim.getId()).getVersion());
    }

    @Test public void claimCancelAcceptsMatchingHighVersion() {
        ClaimRequest claim = highVersionClaim();
        asApplicant();
        claimService.cancel(claim.getId(), cancellation(128));
        assertEquals("CANCELLED", claim(claim.getId()).getStatus());
        assertEquals(Integer.valueOf(129), claim(claim.getId()).getVersion());
    }

    /** 改成数值比较后，真正的过期请求仍须拒绝，不能改动状态或写审核记录。 */
    @Test public void staleHighVersionsStillConflictWithoutWriting() {
        jdbc().update("UPDATE cms_article SET version=129 WHERE id=4");
        asReviewer();
        assertStatus(HttpStatus.CONFLICT, () -> reviewService.decide(4L, reviewDecision(128)));
        assertEquals("PENDING_REVIEW", article(4L).getStatus().name());
        assertEquals(Integer.valueOf(129), article(4L).getVersion());
        assertEquals(0L, reviewRecordMapper.selectCount(null).longValue());

        ClaimRequest claim = highVersionClaim();
        jdbc().update("UPDATE cms_claim_request SET version=129 WHERE id=?", claim.getId());
        assertStatus(HttpStatus.CONFLICT, () -> claimService.decide(claim.getId(), claimDecision(128)));
        asApplicant();
        assertStatus(HttpStatus.CONFLICT, () -> claimService.cancel(claim.getId(), cancellation(128)));
        assertEquals("PENDING", claim(claim.getId()).getStatus());
        assertEquals(Integer.valueOf(129), claim(claim.getId()).getVersion());
    }

    private void approveAt(int version) {
        jdbc().update("UPDATE cms_article SET version=? WHERE id=4", version);
        asReviewer();
        reviewService.decide(4L, reviewDecision(version));
        assertEquals("APPROVED", article(4L).getStatus().name());
        assertEquals(Integer.valueOf(version + 1), article(4L).getVersion());
        assertEquals(1L, reviewRecordMapper.selectCount(null).longValue());
    }

    private ClaimRequest highVersionClaim() {
        ClaimRequest claim = givenClaim(1L, 2L, "PENDING");
        jdbc().update("UPDATE cms_claim_request SET version=128 WHERE id=?", claim.getId());
        return claim;
    }

    private ReviewDecisionRequest reviewDecision(int version) {
        ReviewDecisionRequest request = new ReviewDecisionRequest();
        request.setVersion(Integer.valueOf(String.valueOf(version)));
        request.setDecision(ReviewDecision.APPROVED);
        return request;
    }

    private ClaimDecisionRequest claimDecision(int version) {
        ClaimDecisionRequest request = new ClaimDecisionRequest();
        request.setVersion(Integer.valueOf(String.valueOf(version)));
        request.setDecision(ClaimDecision.APPROVED);
        return request;
    }

    private ClaimCancelRequest cancellation(int version) {
        ClaimCancelRequest request = new ClaimCancelRequest();
        request.setVersion(Integer.valueOf(String.valueOf(version)));
        request.setReason("已经找到物品，撤销本次申请");
        return request;
    }

    private JdbcTemplate jdbc() { return new JdbcTemplate(source); }
}
