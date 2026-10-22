package com.team.cms.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * cms_review_record：一次审核决定的追加记录。
 *
 * <p>不是通用操作日志的替代品；它保存当时审核的物品版本、决定、意见和审核人，
 * 必须与内容状态更新处于同一业务事务。</p>
 */
@TableName("cms_review_record")
public class ReviewRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private Long reviewerId;
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
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Integer getArticleVersion() { return articleVersion; }
    public void setArticleVersion(Integer articleVersion) { this.articleVersion = articleVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
