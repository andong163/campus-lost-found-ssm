package com.team.cms.article.dto;

import com.team.cms.common.api.PageQuery;
import com.team.cms.common.domain.ArticleStatus;

public class MyArticleQuery extends PageQuery {
    private ArticleStatus status;
    public ArticleStatus getStatus() { return status; }
    public void setStatus(ArticleStatus status) { this.status=status; }
}
