package com.team.cms.article.service;

import com.team.cms.article.dto.PrivateArticleDetail;
import com.team.cms.article.dto.CreateDraftRequest;
import com.team.cms.article.dto.EditArticleRequest;
import com.team.cms.article.dto.MyArticleQuery;
import com.team.cms.common.api.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.stream.Collectors;
import com.team.cms.article.entity.Article;
import com.team.cms.article.mapper.ArticleMapper;
import com.team.cms.auth.security.CurrentUserContext;
import com.team.cms.category.service.CategoryAccessService;
import com.team.cms.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 本人草稿查询服务；作者身份只取自登录会话。 */
@Service
public class ArticleDraftService {
    private final ArticleMapper articleMapper;
    private final CategoryAccessService categoryAccessService;

    public ArticleDraftService(ArticleMapper articleMapper, CategoryAccessService categoryAccessService) {
        this.articleMapper = articleMapper;
        this.categoryAccessService = categoryAccessService;
    }

    /** 先校验统一分类，再用会话作者身份创建草稿；状态与版本由服务端维护。 */
    @Transactional
    public PrivateArticleDetail createMyDraft(CreateDraftRequest request) {
        long authorId = CurrentUserContext.require().getId();
        categoryAccessService.requireBindable(request.getCategoryId());
        Article article = request.toNewArticle(authorId);
        if (articleMapper.insert(article) != 1) {
            throw new IllegalStateException("草稿保存失败");
        }
        return PrivateArticleDetail.fromAuthorizedArticle(article);
    }

    /** 仅返回当前作者的未删除草稿，对其他作者统一隐藏为 404。 */
    @Transactional(readOnly = true)
    public PrivateArticleDetail getMyDraft(long id) {
        long authorId = CurrentUserContext.require().getId();
        Article article = articleMapper.selectDraftByIdAndAuthor(id, authorId);
        if (article == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "草稿不存在");
        }
        return PrivateArticleDetail.fromAuthorizedArticle(article);
    }

    @Transactional(readOnly = true)
    public PageResult<PrivateArticleDetail> listMine(MyArticleQuery query) {
        long authorId = CurrentUserContext.require().getId();
        Page<Article> page = new Page<>(query.getPage(), query.getSize());
        articleMapper.selectMine(page, authorId, query.getStatus());
        return new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(),
                page.getRecords().stream().map(PrivateArticleDetail::fromAuthorizedArticle)
                        .collect(Collectors.toList()));
    }

    @Transactional(readOnly = true)
    public PrivateArticleDetail getMine(long id) {
        Article article = articleMapper.selectByIdAndAuthor(id, CurrentUserContext.require().getId());
        if (article == null) throw new BusinessException(HttpStatus.NOT_FOUND, "信息不存在");
        return PrivateArticleDetail.fromAuthorizedArticle(article);
    }

    @Transactional
    public PrivateArticleDetail editMine(long id, EditArticleRequest request) {
        long authorId = CurrentUserContext.require().getId();
        Article article = articleMapper.selectByIdAndAuthor(id, authorId);
        if (article == null) throw new BusinessException(HttpStatus.NOT_FOUND, "信息不存在");
        if (!article.getVersion().equals(request.getVersion()) ||
                (article.getStatus() != com.team.cms.common.domain.ArticleStatus.DRAFT &&
                 article.getStatus() != com.team.cms.common.domain.ArticleStatus.REJECTED))
            throw new BusinessException(HttpStatus.CONFLICT, "状态或版本已变化，请刷新后重试");
        categoryAccessService.requireBindable(request.getCategoryId());
        article.setTitle(request.getTitle()); article.setContent(request.getContent());
        article.setItemType(request.getItemType()); article.setCategoryId(request.getCategoryId());
        article.setEventTime(request.getEventTime()); article.setLocation(request.getLocation());
        article.setContactInfo(request.getContactInfo());
        if (articleMapper.updateEditable(article, request.getVersion()) != 1)
            throw new BusinessException(HttpStatus.CONFLICT, "状态或版本已变化，请刷新后重试");
        return getMine(id);
    }
}
