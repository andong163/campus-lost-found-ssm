package com.team.cms.article.dto;

import com.team.cms.article.entity.Article;
import com.team.cms.common.domain.ArticleStatus;
import com.team.cms.common.domain.ItemType;
import com.team.cms.common.domain.ResolutionStatus;
import java.time.LocalDateTime;
import javax.validation.constraints.*;

/** 仅包含用户可编辑字段；身份和分类可绑定性须由可信服务校验。 */
public class CreateDraftRequest {
    @NotBlank @Size(min = 2, max = 100) private String title;
    @NotBlank @Size(min = 10, max = 5000) private String content;
    @NotNull private ItemType itemType;
    @NotNull @Positive private Long categoryId;
    @NotNull @PastOrPresent private LocalDateTime eventTime;
    @NotBlank @Size(min = 2, max = 100) private String location;
    @NotBlank @Size(min = 2, max = 100) private String contactInfo;

    /** 仅在完成授权和统一分类可绑定校验后调用。 */
    public Article toNewArticle(long authenticatedAuthorId) {
        if (authenticatedAuthorId <= 0) throw new IllegalArgumentException("authorId must be positive");
        Article article = new Article();
        article.setTitle(title);
        article.setContent(content);
        article.setItemType(itemType);
        article.setCategoryId(categoryId);
        article.setEventTime(eventTime);
        article.setLocation(location);
        article.setContactInfo(contactInfo);
        article.setAuthorId(authenticatedAuthorId);
        article.setStatus(ArticleStatus.DRAFT);
        article.setResolutionStatus(ResolutionStatus.OPEN);
        return article;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public ItemType getItemType() { return itemType; }
    public void setItemType(ItemType itemType) { this.itemType = itemType; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getContactInfo() { return contactInfo; }
    public void setContactInfo(String contactInfo) { this.contactInfo = contactInfo; }
}
