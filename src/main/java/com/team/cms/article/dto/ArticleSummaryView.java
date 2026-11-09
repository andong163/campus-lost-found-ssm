package com.team.cms.article.dto;

import com.team.cms.article.entity.Article;
import com.team.cms.common.domain.*;
import java.time.LocalDateTime;

/** R4 本人详情使用的非敏感投影；原编辑接口契约保持不变。 */
public final class ArticleSummaryView {
    private final Long id;
    private final Integer version;
    private final String title,content,location;
    private final ItemType itemType;
    private final ArticleStatus status;
    private final ResolutionStatus resolutionStatus;
    private final LocalDateTime eventTime;
    public ArticleSummaryView(Article item) {
        id=item.getId(); version=item.getVersion(); title=item.getTitle(); content=item.getContent();
        location=item.getLocation();itemType=item.getItemType();status=item.getStatus();
        resolutionStatus=item.getResolutionStatus();eventTime=item.getEventTime();
    }
    public Long getId(){return id;}
    public Integer getVersion(){return version;}
    public String getTitle(){return title;}
    public String getContent(){return content;}
    public String getLocation(){return location;}
    public ItemType getItemType(){return itemType;}
    public ArticleStatus getStatus(){return status;}
    public ResolutionStatus getResolutionStatus(){return resolutionStatus;}
    public LocalDateTime getEventTime(){return eventTime;}
}
