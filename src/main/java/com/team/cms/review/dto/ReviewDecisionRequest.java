package com.team.cms.review.dto;

import com.team.cms.review.domain.ReviewDecision;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/** 审核决定请求；version 是用户看到的物品版本，过期返回 409。 */
public class ReviewDecisionRequest {
    @NotNull(message = "版本不能为空")
    @Min(value = 0, message = "版本不能为负数")
    private Integer version;

    @NotNull(message = "审核决定不能为空")
    private ReviewDecision decision;

    @Size(max = 500, message = "审核意见不能超过500字")
    private String comment;

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public ReviewDecision getDecision() { return decision; }
    public void setDecision(ReviewDecision decision) { this.decision = decision; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
