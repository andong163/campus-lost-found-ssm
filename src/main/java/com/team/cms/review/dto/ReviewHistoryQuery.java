package com.team.cms.review.dto;

import com.team.cms.common.api.PageQuery;
import com.team.cms.review.domain.ReviewDecision;
import javax.validation.constraints.Positive;

/** 审核历史筛选：按物品或决定查询，排序固定为时间倒序 + ID 倒序。 */
public class ReviewHistoryQuery extends PageQuery {
    @Positive(message = "物品ID必须为正整数")
    private Long articleId;
    private ReviewDecision decision;

    public Long getArticleId() { return articleId; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public ReviewDecision getDecision() { return decision; }
    public void setDecision(ReviewDecision decision) { this.decision = decision; }
}
