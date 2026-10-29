package com.team.cms.article.dto;

import com.team.cms.article.entity.Article;
import com.team.cms.common.domain.ArticleStatus;
import com.team.cms.common.domain.ItemType;
import com.team.cms.common.domain.ResolutionStatus;
import java.time.LocalDateTime;

/** 公开返回结构，不包含联系方式和作者身份信息。 */
public final class PublicArticleView {
    private final Long id, categoryId;
    private final String title, content, location;
    private final ItemType itemType;
    private final ResolutionStatus resolutionStatus;
    private final LocalDateTime eventTime, publishedAt;

    private PublicArticleView(Article value) {
        id = value.getId(); categoryId = value.getCategoryId(); title = value.getTitle();
        content = value.getContent(); location = value.getLocation(); itemType = value.getItemType();
        resolutionStatus = value.getResolutionStatus(); eventTime = value.getEventTime();
        publishedAt = value.getPublishedAt();
    }
    /** 调用前须确认所属分类及所有祖先分类仍可公开。 */
    public static PublicArticleView fromVisibleArticle(Article value) {
        if (value.getStatus() != ArticleStatus.PUBLISHED || !Integer.valueOf(0).equals(value.getDeleted())) {
            throw new IllegalArgumentException("article is not public");
        }
        return new PublicArticleView(value);
    }
    public Long getId() { return id; }
    public Long getCategoryId() { return categoryId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getLocation() { return location; }
    public ItemType getItemType() { return itemType; }
    public ResolutionStatus getResolutionStatus() { return resolutionStatus; }
    public LocalDateTime getEventTime() { return eventTime; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
}
