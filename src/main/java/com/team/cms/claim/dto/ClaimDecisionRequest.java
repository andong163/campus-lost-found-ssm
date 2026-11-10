package com.team.cms.claim.dto;

import com.team.cms.claim.domain.ClaimDecision;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/** 审核员核实认领；批准不等于交接，物品仍保持 OPEN。 */
public class ClaimDecisionRequest {
    @NotNull(message = "版本不能为空")
    @Min(value = 0, message = "版本不能为负数")
    private Integer version;

    @NotNull(message = "核实决定不能为空")
    private ClaimDecision decision;

    @Size(max = 500, message = "核实意见不能超过500字")
    private String comment;

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public ClaimDecision getDecision() { return decision; }
    public void setDecision(ClaimDecision decision) { this.decision = decision; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
