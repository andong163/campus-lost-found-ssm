package com.team.cms.article.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.team.cms.common.domain.ArticleStatus;
import com.team.cms.common.domain.ItemType;
import com.team.cms.common.domain.ResolutionStatus;
import com.team.cms.common.persistence.BaseEntity;
import java.time.LocalDateTime;

/** 数据库存储实体，公开接口不得直接返回该对象。 */
@TableName("cms_article")
public class Article extends BaseEntity {
    private String title;
    private String content;
    private Long authorId;
    private Long categoryId;
    private ArticleStatus status;
    private ItemType itemType;
    private LocalDateTime eventTime;
    private String location;
    private String contactInfo;
    private ResolutionStatus resolutionStatus;
    private LocalDateTime resolvedAt;
    private LocalDateTime publishedAt;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public ArticleStatus getStatus() { return status; }
    public void setStatus(ArticleStatus status) { this.status = status; }
    public ItemType getItemType() { return itemType; }
    public void setItemType(ItemType itemType) { this.itemType = itemType; }
    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getContactInfo() { return contactInfo; }
    public void setContactInfo(String contactInfo) { this.contactInfo = contactInfo; }
    public ResolutionStatus getResolutionStatus() { return resolutionStatus; }
    public void setResolutionStatus(ResolutionStatus resolutionStatus) { this.resolutionStatus = resolutionStatus; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
}
