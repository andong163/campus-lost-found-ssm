package com.team.cms;

import com.team.cms.auth.AuthConstants;
import com.team.cms.claim.dto.ClaimCancelRequest;
import com.team.cms.claim.dto.ClaimCreateRequest;
import com.team.cms.claim.dto.ClaimView;
import com.team.cms.support.ReviewClaimTestSupport;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import static org.junit.Assert.assertEquals;

/** 服务层也执行写权限校验，防止漏注解或旧所有者会话绕过只读限制。 */
public class ClaimWriteAccessTest extends ReviewClaimTestSupport {
    @Test public void readOnlyApplicantCannotCreateOrCancelExistingClaim() {
        asApplicant();
        ClaimCreateRequest body = new ClaimCreateRequest();
        body.setClaimDescription("雨伞内侧有本人姓名，可以现场核实。");
        body.setContactInfo("仅供测试");
        ClaimView claim = claimService.apply(1L, body);
        asRole(2L, "只读申请人", AuthConstants.ROLE_VIEWER);
        assertStatus(HttpStatus.FORBIDDEN, () -> claimService.apply(1L, body));
        ClaimCancelRequest cancel = new ClaimCancelRequest();
        cancel.setVersion(0);
        assertStatus(HttpStatus.FORBIDDEN, () -> claimService.cancel(claim.getId(), cancel));
        assertEquals("PENDING", claim(claim.getId()).getStatus());
    }

    @Test public void readOnlyAuthorCannotConfirmResolution() {
        asRole(1L, "只读发布者", AuthConstants.ROLE_VIEWER);
        assertStatus(HttpStatus.FORBIDDEN, () -> itemWorkflowService.resolve(2L, 0, null));
        assertEquals(com.team.cms.common.domain.ResolutionStatus.OPEN, article(2L).getResolutionStatus());
    }
}
