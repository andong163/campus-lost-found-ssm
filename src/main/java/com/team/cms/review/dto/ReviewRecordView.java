package com.team.cms.review.dto;

import java.time.LocalDateTime;

/** 审核记录视图；评论是审核意见，不是认领证据。 */
public final class ReviewRecordView {
    private Long id;
    private Long articleId;
    private Long reviewerId;
    private String reviewerName;
    private String decision;
    private String comment;
    private Integer articleVersion;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getArticleId() { return articleId; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Integer getArticleVersion() { return articleVersion; }
    public void setArticleVersion(Integer articleVersion) { this.articleVersion = articleVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
