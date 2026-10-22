package com.team.cms.article.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.team.cms.common.persistence.BaseEntity;
import java.time.LocalDateTime;

/**
 * cms_article 实体（LOST 寻物与 FOUND 招领共用）。
 *
 * <p>归属袁运轩的 article 模块。杨程的 review/claim 需要“锁定查询 + 条件更新”的稳定契约，
 * 在 YYX R1 尚未合并到 develop 前，本类与 {@code ArticleMapper} 作为最小共享契约先行落地。
 * 袁运轩合并后可以在此实体上补字段、换实现，但必须保留 {@code ArticleMapper} 的锁定查询与
 * 条件状态更新语义，否则审核与认领事务会失去并发保护。</p>
 */
@TableName("cms_article")
public class Article extends BaseEntity {
    private String title;
    private String content;
    private Long authorId;
    private Long categoryId;
    private String status;
    private String itemType;
    private LocalDateTime eventTime;
    private String location;
    private String contactInfo;
    private String resolutionStatus;
    private LocalDateTime resolvedAt;
    private LocalDateTime publishedAt;

    public boolean isFound() { return "FOUND".equals(itemType); }
    public boolean isLost() { return "LOST".equals(itemType); }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getContactInfo() { return contactInfo; }
    public void setContactInfo(String contactInfo) { this.contactInfo = contactInfo; }
    public String getResolutionStatus() { return resolutionStatus; }
    public void setResolutionStatus(String resolutionStatus) { this.resolutionStatus = resolutionStatus; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
}
