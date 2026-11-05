package com.team.cms.article.dto;

import com.team.cms.common.api.PageQuery;
import com.team.cms.common.domain.ArticleStatus;
import javax.validation.constraints.Positive;
public class ManageArticleQuery extends PageQuery {
    private ArticleStatus status;
    @Positive private Long authorId;
    public ArticleStatus getStatus() { return status; } public void setStatus(ArticleStatus v) { status=v; }
    public Long getAuthorId() { return authorId; } public void setAuthorId(Long v) { authorId=v; }
}
