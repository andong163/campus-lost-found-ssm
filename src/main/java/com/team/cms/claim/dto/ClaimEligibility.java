package com.team.cms.claim.dto;

/** 不携带证据或联系方式；原因码供详情入口显示，提交时必须重新校验。 */
public final class ClaimEligibility {
    private final boolean eligible;
    private final String reason;
    public ClaimEligibility(boolean eligible, String reason) { this.eligible=eligible; this.reason=reason; }
    public boolean isEligible() { return eligible; }
    public String getReason() { return reason; }
}
