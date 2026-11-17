package com.team.cms.article.dto;

import com.team.cms.common.api.PageQuery;
import com.team.cms.common.domain.ItemType;
import com.team.cms.common.domain.ResolutionStatus;
import com.team.cms.common.exception.BusinessException;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;

public class PublicArticleQuery extends PageQuery {
    private String keyword, location, sortBy = "publishedAt", direction = "desc";
    private ItemType itemType;
    private ResolutionStatus resolutionStatus;
    private Long categoryId;
    private LocalDateTime from, to;
    public String getKeyword() { return keyword; } public void setKeyword(String v) { keyword=v; }
    public String getLocation() { return location; } public void setLocation(String v) { location=v; }
    public ItemType getItemType() { return itemType; } public void setItemType(ItemType v) { itemType=v; }
    public ResolutionStatus getResolutionStatus() { return resolutionStatus; } public void setResolutionStatus(ResolutionStatus v) { resolutionStatus=v; }
    public Long getCategoryId() { return categoryId; } public void setCategoryId(Long v) { categoryId=v; }
    public LocalDateTime getFrom() { return from; } public void setFrom(LocalDateTime v) { from=v; }
    public LocalDateTime getTo() { return to; } public void setTo(LocalDateTime v) { to=v; }
    public String getSortBy() { return sortBy; } public void setSortBy(String v) { sortBy=v; }
    public String getDirection() { return direction; } public void setDirection(String v) { direction=v; }
    public void validate() {
        if (getPage()<1 || getSize()<1 || getSize()>100 ||
            (from!=null && to!=null && from.isAfter(to)) ||
            (categoryId!=null && categoryId<=0) ||
            !("publishedAt".equals(sortBy) || "eventTime".equals(sortBy)) ||
            !("asc".equalsIgnoreCase(direction) || "desc".equalsIgnoreCase(direction)) ||
            (keyword!=null && keyword.length()>100) || (location!=null && location.length()>100))
            throw new BusinessException(HttpStatus.BAD_REQUEST,"查询条件不合法");
    }
    public String sortColumn() { return "eventTime".equals(sortBy)?"a.event_time":"a.published_at"; }
    public String sortDirection() { return "asc".equalsIgnoreCase(direction)?"ASC":"DESC"; }
    public String keywordPattern() { return pattern(keyword); }
    public String locationPattern() { return pattern(location); }
    public String getKeywordPattern() { return keywordPattern(); }
    public String getLocationPattern() { return locationPattern(); }
    public String getSortColumn() { return sortColumn(); }
    public String getSortDirection() { return sortDirection(); }
    private static String pattern(String v) { return v==null||v.trim().isEmpty()?null:"%"+v.trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%"; }
}
