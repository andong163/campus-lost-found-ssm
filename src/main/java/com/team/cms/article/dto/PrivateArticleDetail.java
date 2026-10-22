package com.team.cms.article.dto;

import com.team.cms.article.entity.Article;
import com.team.cms.common.domain.ArticleStatus;
import com.team.cms.common.domain.ItemType;
import com.team.cms.common.domain.ResolutionStatus;
import java.time.LocalDateTime;

/** 仅供授权后的作者读取，公开接口不得使用此返回结构。 */
public final class PrivateArticleDetail {
    private final Long id, authorId, categoryId;
    private final String title, content, location, contactInfo;
    private final ItemType itemType;
    private final ArticleStatus status;
    private final ResolutionStatus resolutionStatus;
    private final LocalDateTime eventTime, resolvedAt, publishedAt, createdAt, updatedAt;
    private final Integer version;

    private PrivateArticleDetail(Article value) {
        id = value.getId(); authorId = value.getAuthorId(); categoryId = value.getCategoryId();
        title = value.getTitle(); content = value.getContent(); location = value.getLocation();
        contactInfo = value.getContactInfo(); itemType = value.getItemType();
        status = value.getStatus(); resolutionStatus = value.getResolutionStatus();
        eventTime = value.getEventTime(); resolvedAt = value.getResolvedAt();
        publishedAt = value.getPublishedAt(); createdAt = value.getCreatedAt();
        updatedAt = value.getUpdatedAt(); version = value.getVersion();
    }
    public static PrivateArticleDetail fromAuthorizedArticle(Article value) {
        return new PrivateArticleDetail(value);
    }
    public Long getId() { return id; }
    public Long getAuthorId() { return authorId; }
    public Long getCategoryId() { return categoryId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getLocation() { return location; }
    public String getContactInfo() { return contactInfo; }
    public ItemType getItemType() { return itemType; }
    public ArticleStatus getStatus() { return status; }
    public ResolutionStatus getResolutionStatus() { return resolutionStatus; }
    public LocalDateTime getEventTime() { return eventTime; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Integer getVersion() { return version; }
}
