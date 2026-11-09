package com.team.cms.claim.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/** 申请人撤销自己的待核实或已通过申请；必须说明原因。 */
public class ClaimCancelRequest {
    @NotNull(message = "版本不能为空")
    @Min(value = 0, message = "版本不能为负数")
    private Integer version;

    @NotBlank(message = "撤销原因不能为空")
    @Size(min = 2, max = 500, message = "撤销原因需为2到500字")
    private String reason;

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
