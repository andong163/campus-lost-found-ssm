package com.team.cms.claim.dto;

import java.time.LocalDateTime;

/**
 * 认领申请视图。包含私有说明与联系方式，只能通过 /claims/mine、/claims、
 * /items/{id}/claims 这些已做所有权/权限检查的接口返回，禁止进入公开物品 DTO。
 */
public final class ClaimView {
    private Long id;
    private Long articleId;
    private String articleTitle;
    private String itemType;
    private Long applicantId;
    private String applicantName;
    private String claimDescription;
    private String contactInfo;
    private String status;
    private Long reviewerId;
    private String reviewerName;
    private String reviewComment;
    private String cancelReason;
    private LocalDateTime reviewedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private Integer version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getArticleId() { return articleId; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public String getArticleTitle() { return articleTitle; }
    public void setArticleTitle(String articleTitle) { this.articleTitle = articleTitle; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public String getClaimDescription() { return claimDescription; }
    public void setClaimDescription(String claimDescription) { this.claimDescription = claimDescription; }
    public String getContactInfo() { return contactInfo; }
    public void setContactInfo(String contactInfo) { this.contactInfo = contactInfo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
