package com.team.cms.article.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.team.cms.article.dto.PrivateArticleDetail;
import com.team.cms.article.entity.Article;
import com.team.cms.article.mapper.ArticleMapper;
import com.team.cms.auth.security.CurrentUser;
import com.team.cms.auth.security.CurrentUserContext;
import com.team.cms.category.service.CategoryAccessService;
import com.team.cms.claim.mapper.ClaimRequestMapper;
import com.team.cms.common.api.PageResult;
import com.team.cms.common.domain.ArticleStatus;
import com.team.cms.common.exception.BusinessException;
import com.team.cms.review.dto.ReviewRecordView;
import com.team.cms.review.entity.ReviewRecord;
import com.team.cms.review.mapper.ReviewRecordMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Single state transition owner. YC can extend this contract for later rounds. */
@Service
public class ItemWorkflowService {
    private final ArticleMapper articles;
    private final ReviewRecordMapper reviews;
    private final ClaimRequestMapper claims;
    private final CategoryAccessService categories;

    public ItemWorkflowService(ArticleMapper articles, ReviewRecordMapper reviews,
            ClaimRequestMapper claims, CategoryAccessService categories) {
        this.articles=articles; this.reviews=reviews; this.claims=claims; this.categories=categories;
    }

    private Article locked(long id, int version) {
        Article item = articles.selectByIdForUpdate(id);
        if (item == null) throw new BusinessException(HttpStatus.NOT_FOUND, "信息不存在");
        if (!item.getVersion().equals(version)) conflict();
        return item;
    }
    private void own(Article item) {
        if (!item.getAuthorId().equals(CurrentUserContext.require().getId()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "只能维护本人信息");
    }
    private void conflict() { throw new BusinessException(HttpStatus.CONFLICT, "状态或版本已变化，请刷新后重试"); }
    private PrivateArticleDetail move(Article item, ArticleStatus from, ArticleStatus to, int version) {
        if (item.getStatus() != from || articles.updateStatusIfVersion(item.getId(), from, to, version) != 1) conflict();
        return PrivateArticleDetail.fromAuthorizedArticle(articles.selectById(item.getId()));
    }
    @Transactional public PrivateArticleDetail submit(long id, int version) {
        Article item=locked(id,version); own(item);
        if (item.getStatus()!=ArticleStatus.DRAFT && item.getStatus()!=ArticleStatus.REJECTED) conflict();
        categories.requireBindable(item.getCategoryId());
        return move(item,item.getStatus(),ArticleStatus.PENDING_REVIEW,version);
    }
    @Transactional public PrivateArticleDetail withdraw(long id, int version) {
        Article item=locked(id,version); own(item);
        return move(item,ArticleStatus.PENDING_REVIEW,ArticleStatus.DRAFT,version);
    }
    @Transactional public PrivateArticleDetail offline(long id, int version) {
        Article item=locked(id,version); own(item);
        if (claims.countApprovedByItem(id)>0) conflict();
        PrivateArticleDetail result=move(item,ArticleStatus.PUBLISHED,ArticleStatus.OFFLINE,version);
        claims.closePendingByItem(id,"信息已下架");
        return result;
    }
    @Transactional public PrivateArticleDetail toDraft(long id, int version) {
        Article item=locked(id,version); own(item);
        if (claims.countApprovedByItem(id)>0) conflict();
        return move(item,ArticleStatus.OFFLINE,ArticleStatus.DRAFT,version);
    }
    @Transactional public PrivateArticleDetail decide(long id, int version, boolean approved, String comment) {
        CurrentUser reviewer=CurrentUserContext.require();
        Article item=locked(id,version);
        if (item.getAuthorId().equals(reviewer.getId()))
            throw new BusinessException(HttpStatus.FORBIDDEN,"不能审核本人信息");
        if (!approved && (comment==null || comment.trim().isEmpty()))
            throw new BusinessException(HttpStatus.BAD_REQUEST,"驳回意见不能为空");
        if (item.getStatus()!=ArticleStatus.PENDING_REVIEW) conflict();
        categories.requireBindable(item.getCategoryId());
        ArticleStatus next=approved?ArticleStatus.APPROVED:ArticleStatus.REJECTED;
        move(item,ArticleStatus.PENDING_REVIEW,next,version);
        ReviewRecord record=new ReviewRecord();
        record.setArticleId(id); record.setReviewerId(reviewer.getId());
        record.setDecision(next.name()); record.setComment(comment==null?"":comment.trim());
        record.setArticleVersion(version+1); record.setCreatedAt(LocalDateTime.now());
        if (reviews.insert(record)!=1) throw new IllegalStateException("审核记录保存失败");
        return PrivateArticleDetail.fromAuthorizedArticle(articles.selectById(id));
    }
    @Transactional public PrivateArticleDetail publish(long id, int version) {
        Article item=locked(id,version);
        if (item.getStatus()!=ArticleStatus.APPROVED) conflict();
        categories.requireBindable(item.getCategoryId());
        if (articles.updateStatus(id,ArticleStatus.APPROVED.name(),ArticleStatus.PUBLISHED.name(),
                version,LocalDateTime.now())!=1) conflict();
        return PrivateArticleDetail.fromAuthorizedArticle(articles.selectById(id));
    }
    @Transactional(readOnly=true) public List<ReviewRecordView> myReviews(long id) {
        Article item=articles.selectByIdAndAuthor(id,CurrentUserContext.require().getId());
        if (item==null) throw new BusinessException(HttpStatus.NOT_FOUND,"信息不存在");
        return reviews.selectViewsByArticleId(id);
    }
    @Transactional(readOnly=true) public PageResult<PrivateArticleDetail> queue(int page, int size) {
        Page<Article> result=new Page<>(page,size);
        articles.selectReviewQueue(result);
        return new PageResult<>(result.getCurrent(),result.getSize(),result.getTotal(),
            result.getRecords().stream().map(PrivateArticleDetail::fromAuthorizedArticle)
                .collect(java.util.stream.Collectors.toList()));
    }
    @Transactional(readOnly=true) public PageResult<PrivateArticleDetail> approvedQueue(int page, int size) {
        Page<Article> result=new Page<>(page,size);
        articles.selectApprovedQueue(result);
        return new PageResult<>(result.getCurrent(),result.getSize(),result.getTotal(),
            result.getRecords().stream().map(PrivateArticleDetail::fromAuthorizedArticle)
                .collect(java.util.stream.Collectors.toList()));
    }
}
