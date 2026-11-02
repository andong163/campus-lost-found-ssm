package com.team.cms.review.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.team.cms.article.entity.Article;
import com.team.cms.article.mapper.ArticleMapper;
import com.team.cms.auth.AuthConstants;
import com.team.cms.auth.security.CurrentUser;
import com.team.cms.auth.security.CurrentUserContext;
import com.team.cms.common.api.PageResult;
import com.team.cms.common.domain.ArticleStatus;
import com.team.cms.common.exception.BusinessException;
import com.team.cms.review.ReviewPermissions;
import com.team.cms.common.security.PermissionCodes;
import com.team.cms.review.domain.ReviewDecision;
import com.team.cms.review.dto.ReviewDecisionRequest;
import com.team.cms.review.dto.ReviewHistoryQuery;
import com.team.cms.review.dto.ReviewRecordView;
import com.team.cms.review.dto.ReviewTaskQuery;
import com.team.cms.review.dto.ReviewTaskView;
import com.team.cms.review.entity.ReviewRecord;
import com.team.cms.review.mapper.ReviewRecordMapper;
import com.team.cms.review.mapper.ReviewTaskMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 审核工作台实现。
 *
 * <p>审核决定只处置 PENDING_REVIEW：先锁物品行，拒绝本人审核，校验 version，再条件更新状态，
 * 并在同一事务追加 cms_review_record。任何一步失败都回滚，不留下“状态变了但记录没写”的半完成数据。</p>
 */
@Service
public class ReviewServiceImpl implements ReviewService {
    private static final int MAX_COMMENT = 500;

    private final ArticleMapper articleMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final ReviewTaskMapper reviewTaskMapper;

    public ReviewServiceImpl(ArticleMapper articleMapper, ReviewRecordMapper reviewRecordMapper,
            ReviewTaskMapper reviewTaskMapper) {
        this.articleMapper = articleMapper;
        this.reviewRecordMapper = reviewRecordMapper;
        this.reviewTaskMapper = reviewTaskMapper;
    }

    @Override
    public PageResult<ReviewTaskView> pending(ReviewTaskQuery query) {
        CurrentUser actor = CurrentUserContext.require();
        requireReviewPermission(actor);
        // 审核员不能处理本人内容，待办列表也不展示，避免界面提供必然 403 的按钮。
        query.setExcludeAuthorId(actor.getId());
        Page<ReviewTaskView> page = new Page<>(query.getPage(), query.getSize());
        return PageResult.from(reviewTaskMapper.selectPending(page, query));
    }

    @Override
    public PageResult<ReviewRecordView> history(ReviewHistoryQuery query) {
        requireReviewPermission(CurrentUserContext.require());
        Page<ReviewRecordView> page = new Page<>(query.getPage(), query.getSize());
        return PageResult.from(reviewRecordMapper.selectHistory(page, query));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void decide(Long itemId, ReviewDecisionRequest request) {
        CurrentUser actor = CurrentUserContext.require();
        if (!actor.hasRole(AuthConstants.ROLE_ADMIN) && !actor.hasPermission(ReviewPermissions.REVIEW_HANDLE)
                && !actor.hasPermission(PermissionCodes.REVIEW_DECIDE))
            throw new BusinessException(HttpStatus.FORBIDDEN,"无审核权限");
        Article article = articleMapper.selectByIdForUpdate(itemId);
        if (article == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "信息不存在");
        }
        if (article.getAuthorId().equals(actor.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "不能审核本人信息");
        }
        if (article.getStatus() != ArticleStatus.PENDING_REVIEW) {
            throw conflict("只有待审核信息可以处理，请刷新后重试");
        }
        if (article.getVersion() == null || article.getVersion() != request.getVersion()) {
            throw conflict("信息版本已变化，请刷新后重试");
        }
        String comment = normalizeComment(request.getComment());
        if (request.getDecision() == ReviewDecision.REJECTED && !StringUtils.hasText(comment)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "驳回必须填写审核意见");
        }
        int updated = articleMapper.updateStatus(itemId, ArticleStatus.PENDING_REVIEW.name(),
            request.getDecision().name(), request.getVersion(), null);
        if (updated != 1) {
            throw conflict("信息状态或版本已变化，请刷新后重试");
        }
        ReviewRecord record = new ReviewRecord();
        record.setArticleId(article.getId());
        record.setReviewerId(actor.getId());
        record.setDecision(request.getDecision().name());
        record.setComment(comment == null ? "" : comment);
        record.setArticleVersion(request.getVersion());
        record.setCreatedAt(LocalDateTime.now());
        if (reviewRecordMapper.insert(record) != 1) throw new IllegalStateException("审核记录保存失败");
    }

    @Override
    public List<ReviewRecordView> records(Long itemId) {
        CurrentUser actor = CurrentUserContext.require();
        Article article = articleMapper.selectById(itemId);
        if (article == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "信息不存在");
        }
        boolean owner = article.getAuthorId().equals(actor.getId());
        if (!owner && !isReviewer(actor)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权查看该信息的审核记录");
        }
        return reviewRecordMapper.selectViewsByArticleId(itemId);
    }

    private static void requireReviewPermission(CurrentUser actor) {
        if (!isReviewer(actor)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无审核权限");
        }
    }

    private static boolean isReviewer(CurrentUser actor) {
        return actor.hasRole(AuthConstants.ROLE_ADMIN)
            || actor.hasPermission(ReviewPermissions.REVIEW_HANDLE)
            || actor.hasPermission(PermissionCodes.REVIEW_VIEW)
            || actor.hasPermission(PermissionCodes.REVIEW_DECIDE);
    }

    private static String normalizeComment(String comment) {
        if (!StringUtils.hasText(comment)) {
            return "";
        }
        String text = comment.trim();
        return text.length() <= MAX_COMMENT ? text : text.substring(0, MAX_COMMENT);
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }
}
