package com.team.cms.claim.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

/**
 * 结案请求：FOUND 由发布者确认实际交接（可指定 expectedClaimId），LOST 由作者确认已找回。
 * 管理员例外关闭不在第一版开放，避免伪造交接证据。
 */
public class ResolveRequest {
    @NotNull(message = "版本不能为空")
    @Min(value = 0, message = "版本不能为负数")
    private Integer version;

    @Positive(message = "认领ID必须为正整数")
    private Long claimId;

    @Size(max = 500, message = "结案说明不能超过500字")
    private String reason;

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Long getClaimId() { return claimId; }
    public void setClaimId(Long claimId) { this.claimId = claimId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
