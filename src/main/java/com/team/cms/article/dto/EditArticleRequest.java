package com.team.cms.article.dto;

import com.team.cms.common.api.VersionRequest;
import com.team.cms.common.domain.ItemType;
import java.time.LocalDateTime;
import javax.validation.constraints.*;

/** Only author-editable fields are accepted. */
public class EditArticleRequest extends VersionRequest {
    @NotBlank @Size(min=2, max=100) private String title;
    @NotBlank @Size(min=10, max=5000) private String content;
    @NotNull private ItemType itemType;
    @NotNull @Positive private Long categoryId;
    @NotNull @PastOrPresent private LocalDateTime eventTime;
    @NotBlank @Size(min=2, max=100) private String location;
    @NotBlank @Size(min=2, max=100) private String contactInfo;
    public String getTitle() { return title; }
    public void setTitle(String v) { title=v; }
    public String getContent() { return content; }
    public void setContent(String v) { content=v; }
    public ItemType getItemType() { return itemType; }
    public void setItemType(ItemType v) { itemType=v; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long v) { categoryId=v; }
    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime v) { eventTime=v; }
    public String getLocation() { return location; }
    public void setLocation(String v) { location=v; }
    public String getContactInfo() { return contactInfo; }
    public void setContactInfo(String v) { contactInfo=v; }
}
