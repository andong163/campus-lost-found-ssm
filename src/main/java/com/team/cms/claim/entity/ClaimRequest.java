package com.team.cms.claim.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * cms_claim_request：认领申请与核实/交接历史。
 *
 * <p>数据库中的 active_applicant_id、approved_article_id 是生成列，用于两个唯一约束：
 * 同人同物最多一条 PENDING/APPROVED，单物品最多一条 APPROVED。生成列不映射到实体，
 * 避免 INSERT/UPDATE 误写；并发保护由数据库唯一索引 + 物品行锁共同完成。</p>
 */
@TableName("cms_claim_request")
public class ClaimRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private Long applicantId;
    private String claimDescription;
    private String contactInfo;
    private String status;
    private Long reviewerId;
    private String reviewComment;
    private String cancelReason;
    private LocalDateTime reviewedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getArticleId() { return articleId; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
    public String getClaimDescription() { return claimDescription; }
    public void setClaimDescription(String claimDescription) { this.claimDescription = claimDescription; }
    public String getContactInfo() { return contactInfo; }
    public void setContactInfo(String contactInfo) { this.contactInfo = contactInfo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
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
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
