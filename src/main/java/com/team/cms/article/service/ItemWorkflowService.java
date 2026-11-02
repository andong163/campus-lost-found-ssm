package com.team.cms.article.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.team.cms.article.dto.PrivateArticleDetail;
import com.team.cms.article.entity.Article;
import com.team.cms.article.mapper.ArticleMapper;
import com.team.cms.auth.AuthConstants;
import com.team.cms.auth.security.CurrentUser;
import com.team.cms.auth.security.CurrentUserContext;
import com.team.cms.common.api.PageResult;
import com.team.cms.common.exception.BusinessException;
import com.team.cms.common.security.PermissionCodes;
import com.team.cms.review.domain.ReviewDecision;
import com.team.cms.review.dto.ReviewDecisionRequest;
import com.team.cms.review.dto.ReviewRecordView;
import com.team.cms.review.service.ReviewService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 袁运轩页面的响应适配层。状态命令仅委托杨程的工作流，不维护第二套状态规则。 */
@Service
public class ItemWorkflowService {
    private final ArticleMapper articles;
    private final ArticleDraftService drafts;
    private final ReviewService reviews;
    private final com.team.cms.review.service.ItemWorkflowService workflow;
    public ItemWorkflowService(ArticleMapper articles, ArticleDraftService drafts, ReviewService reviews,
            com.team.cms.review.service.ItemWorkflowService workflow) {
        this.articles=articles; this.drafts=drafts; this.reviews=reviews; this.workflow=workflow;
    }
    @Transactional public PrivateArticleDetail submit(long id, int version) {
        workflow.submit(id,version); return detail(id);
    }
    @Transactional public PrivateArticleDetail withdraw(long id, int version) {
        workflow.withdraw(id,version); return detail(id);
    }
    @Transactional public PrivateArticleDetail offline(long id, int version) {
        drafts.getMine(id); workflow.offline(id,version,null); return detail(id);
    }
    @Transactional public PrivateArticleDetail toDraft(long id, int version) {
        workflow.toDraft(id,version); return detail(id);
    }
    @Transactional public PrivateArticleDetail decide(long id, int version, boolean approved, String comment) {
        ReviewDecisionRequest decision=new ReviewDecisionRequest();
        decision.setVersion(version); decision.setComment(comment);
        decision.setDecision(approved?ReviewDecision.APPROVED:ReviewDecision.REJECTED);
        reviews.decide(id,decision); return detail(id);
    }
    @Transactional public PrivateArticleDetail publish(long id, int version) {
        workflow.publish(id,version); return detail(id);
    }
    @Transactional(readOnly=true) public List<ReviewRecordView> myReviews(long id) {
        drafts.getMine(id); return reviews.records(id);
    }
    @Transactional(readOnly=true) public PageResult<PrivateArticleDetail> queue(int page, int size) {
        requirePermission(PermissionCodes.REVIEW_VIEW);
        Page<Article> result=new Page<>(page,size);
        articles.selectReviewQueue(result,CurrentUserContext.require().getId()); return result(result);
    }
    @Transactional(readOnly=true) public PageResult<PrivateArticleDetail> approvedQueue(int page, int size) {
        requirePermission(PermissionCodes.ITEM_PUBLISH);
        Page<Article> result=new Page<>(page,size); articles.selectApprovedQueue(result); return result(result);
    }
    private PrivateArticleDetail detail(long id) {
        return PrivateArticleDetail.fromAuthorizedArticle(articles.selectById(id));
    }
    private PageResult<PrivateArticleDetail> result(Page<Article> page) {
        return new PageResult<>(page.getCurrent(),page.getSize(),page.getTotal(),
            page.getRecords().stream().map(PrivateArticleDetail::fromAuthorizedArticle).collect(Collectors.toList()));
    }
    private void requirePermission(String code) {
        CurrentUser actor=CurrentUserContext.require();
        if (!actor.hasRole(AuthConstants.ROLE_ADMIN) && !actor.hasPermission(code))
            throw new BusinessException(HttpStatus.FORBIDDEN,"无权限访问");
    }
}
