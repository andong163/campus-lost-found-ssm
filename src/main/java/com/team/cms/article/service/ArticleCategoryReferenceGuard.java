package com.team.cms.article.service;

import com.team.cms.article.mapper.ArticleMapper;
import com.team.cms.category.service.CategoryReferenceGuard;
import org.springframework.stereotype.Component;

/** 内容模块登记真实引用；与分类删除使用的分类行锁配合。 */
@Component
public class ArticleCategoryReferenceGuard implements CategoryReferenceGuard {
    private final ArticleMapper articles;
    public ArticleCategoryReferenceGuard(ArticleMapper articles) { this.articles=articles; }
    @Override public String blockingReason(Long categoryId) {
        return articles.countCategoryReferences(categoryId)>0 ? "该分类下还有未删除的物品信息，不能删除" : null;
    }
}
