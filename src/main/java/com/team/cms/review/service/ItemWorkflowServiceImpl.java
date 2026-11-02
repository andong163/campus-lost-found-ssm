package com.team.cms.review.service;

import com.team.cms.article.entity.Article;
import com.team.cms.article.mapper.ArticleMapper;
import com.team.cms.auth.AuthConstants;
import com.team.cms.auth.security.CurrentUser;
import com.team.cms.auth.security.CurrentUserContext;
import com.team.cms.category.service.CategoryAccessService;
import com.team.cms.claim.entity.ClaimRequest;
import com.team.cms.claim.mapper.ClaimRequestMapper;
import com.team.cms.common.domain.ArticleStatus;
import com.team.cms.common.domain.ItemType;
import com.team.cms.common.domain.ResolutionStatus;
import com.team.cms.common.exception.BusinessException;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 内容状态机实现。
 *
 * <p>关键约束：</p>
 * <ul>
 *   <li>先 {@code SELECT ... FOR UPDATE} 锁物品行，再校验所有权/状态/version，最后条件 UPDATE；</li>
 *   <li>状态条件与 version 同时进入 WHERE，影响 0 行一律 409，不信任客户端目标状态；</li>
 *   <li>已通过认领存在时禁止下架、改稿和删除；下架时同事务关闭 PENDING 申请；</li>
 *   <li>审核批准、公开发布、认领核实、实际交接是四个不同动作，不合并。</li>
 * </ul>
 */
@Service
public class ItemWorkflowServiceImpl implements ItemWorkflowService {

    private static final String ARTICLE_PUBLISH = "article:publish";
    private static final String DEFAULT_OFFLINE_REASON = "信息已下架，待核实申请自动关闭";
    private static final int MAX_TEXT = 500;

    private final ArticleMapper articleMapper;
    private final CategoryAccessService categoryAccessService;
    private final ClaimRequestMapper claimRequestMapper;

    public ItemWorkflowServiceImpl(ArticleMapper articleMapper,
            CategoryAccessService categoryAccessService,
            ClaimRequestMapper claimRequestMapper) {
        this.articleMapper = articleMapper;
        this.categoryAccessService = categoryAccessService;
        this.claimRequestMapper = claimRequestMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long itemId, int version) {
        CurrentUser actor = CurrentUserContext.require();
        Article article = lock(itemId);
        requireAuthor(article, actor);
        requireStatus(article, ArticleStatus.DRAFT, ArticleStatus.REJECTED);
        requireVersion(article, version);
        assertContentComplete(article);
        categoryAccessService.requireBindable(article.getCategoryId());
        changeStatus(article, version, ArticleStatus.PENDING_REVIEW, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long itemId, int version) {
        CurrentUser actor = CurrentUserContext.require();
        Article article = lock(itemId);
        requireAuthor(article, actor);
        requireStatus(article, ArticleStatus.PENDING_REVIEW);
        requireVersion(article, version);
        changeStatus(article, version, ArticleStatus.DRAFT, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toDraft(Long itemId, int version) {
        CurrentUser actor = CurrentUserContext.require();
        Article article = lock(itemId);
        requireAuthor(article, actor);
        requireStatus(article, ArticleStatus.APPROVED, ArticleStatus.OFFLINE);
        requireVersion(article, version);
        if (claimRequestMapper.countActiveByItem(itemId) > 0) {
            throw conflict("存在未完成的认领申请，不能转为草稿");
        }
        changeStatus(article, version, ArticleStatus.DRAFT, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long itemId, int version) {
        CurrentUser actor = CurrentUserContext.require();
        requirePublishPermission(actor);
        Article article = lock(itemId);
        requireStatus(article, ArticleStatus.APPROVED, ArticleStatus.OFFLINE);
        requireVersion(article, version);
        categoryAccessService.requireBindable(article.getCategoryId());
        if (article.getResolutionStatus() != ResolutionStatus.OPEN) {
            throw conflict("已结案的信息不能发布");
        }
        if (claimRequestMapper.countApprovedByItem(itemId) > 0) {
            throw conflict("存在已通过的认领，不能重新发布");
        }
        changeStatus(article, version, ArticleStatus.PUBLISHED, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offline(Long itemId, int version, String reason) {
        CurrentUser actor = CurrentUserContext.require();
        Article article = lock(itemId);
        if (!article.getAuthorId().equals(actor.getId())) {
            requirePublishPermission(actor);
        }
        requireStatus(article, ArticleStatus.PUBLISHED);
        requireVersion(article, version);
        if (claimRequestMapper.countApprovedByItem(itemId) > 0) {
            throw conflict("存在已通过的认领，请先完成或撤销后再下架");
        }
        String closeReason = safeReason(reason);
        changeStatus(article, version, ArticleStatus.OFFLINE, null);
        claimRequestMapper.closePendingByItem(itemId, closeReason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assertDeletable(Long itemId) {
        Article article = lock(itemId);
        if (article.getStatus() == ArticleStatus.PENDING_REVIEW) {
            throw conflict("待审核信息不能删除，请先撤回");
        }
        if (claimRequestMapper.countActiveByItem(itemId) > 0) {
            throw conflict("存在未完成的认领申请，不能删除");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolve(Long itemId, int version, Long expectedClaimId) {
        CurrentUser actor = CurrentUserContext.require();
        Article article = lock(itemId);
        requireAuthor(article, actor);
        if (article.getResolutionStatus() == ResolutionStatus.RESOLVED) {
            throw conflict("信息已结案，不能重复确认");
        }
        requireStatus(article, ArticleStatus.PUBLISHED);
        requireVersion(article, version);
        if (article.isLost()) {
            if (expectedClaimId != null) {
                throw conflict("寻物信息没有认领申请，不能指定认领");
            }
            resolveArticle(article, version);
            return;
        }
        if (!article.isFound()) {
            throw conflict("信息类型不合法，无法结案");
        }
        resolveFoundHandover(article, version, expectedClaimId);
    }

    private void resolveFoundHandover(Article article, int version, Long expectedClaimId) {
        ClaimRequest approved = claimRequestMapper.selectApprovedForUpdate(article.getId());
        if (approved == null) {
            throw conflict("招领信息没有已通过的认领，不能确认交接");
        }
        if (expectedClaimId != null && !expectedClaimId.equals(approved.getId())) {
            throw conflict("指定的认领不是当前已通过申请，请刷新后重试");
        }
        // 先完成申请，再写物品结案；任一步失败都由同一事务回滚。
        int completed = claimRequestMapper.complete(approved.getId(),
            LocalDateTime.now(), approved.getVersion());
        if (completed != 1) {
            throw conflict("认领状态或版本已变化，请刷新后重试");
        }
        resolveArticle(article, version);
        claimRequestMapper.closePendingByItem(article.getId(), "物品已完成交接，其他待核实申请关闭");
    }

    private void resolveArticle(Article article, int version) {
        int resolved = articleMapper.resolve(article.getId(), version, LocalDateTime.now());
        if (resolved != 1) {
            throw conflict("物品已结案或版本已变化，请刷新后重试");
        }
    }

    private void changeStatus(Article article, int version, ArticleStatus target,
            LocalDateTime publishedAt) {
        int updated = articleMapper.updateStatus(article.getId(), article.getStatus().name(),
            target.name(), version, publishedAt);
        if (updated != 1) {
            throw conflict("状态或版本已变化，请刷新后重试");
        }
    }

    private Article lock(Long itemId) {
        Article article = itemId == null ? null : articleMapper.selectByIdForUpdate(itemId);
        if (article == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "信息不存在");
        }
        return article;
    }

    private static void requireAuthor(Article article, CurrentUser actor) {
        if (!article.getAuthorId().equals(actor.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只能操作本人信息");
        }
    }

    private static void requireStatus(Article article, ArticleStatus... allowed) {
        for (ArticleStatus status : allowed) {
            if (article.getStatus() == status) {
                return;
            }
        }
        throw conflict("当前状态不允许该操作");
    }

    private static void requireVersion(Article article, int version) {
        if (article.getVersion() == null || article.getVersion() != version) {
            throw conflict("信息版本已变化，请刷新后重试");
        }
    }

    private static void requirePublishPermission(CurrentUser actor) {
        if (actor.hasRole(AuthConstants.ROLE_ADMIN) || actor.hasPermission(ARTICLE_PUBLISH) || actor.hasPermission(com.team.cms.common.security.PermissionCodes.ITEM_PUBLISH)) {
            return;
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "无发布权限");
    }

    private static void assertContentComplete(Article article) {
        if (!StringUtils.hasText(article.getTitle())
                || article.getTitle().trim().length() < 2 || article.getTitle().trim().length() > 100) {
            throw conflict("标题需为2到100字，才能提交审核");
        }
        if (!StringUtils.hasText(article.getContent())
                || article.getContent().trim().length() < 10
                || article.getContent().trim().length() > 5000) {
            throw conflict("物品描述需为10到5000字，才能提交审核");
        }
        if (!StringUtils.hasText(article.getLocation())
                || article.getLocation().trim().length() < 2
                || article.getLocation().trim().length() > 100) {
            throw conflict("地点需为2到100字，才能提交审核");
        }
        if (!StringUtils.hasText(article.getContactInfo())
                || article.getContactInfo().trim().length() < 2
                || article.getContactInfo().trim().length() > 100) {
            throw conflict("联系方式需为2到100字，才能提交审核");
        }
        if (article.getCategoryId() == null) {
            throw conflict("请选择分类后再提交审核");
        }
        if (article.getEventTime() == null || article.getEventTime().isAfter(LocalDateTime.now())) {
            throw conflict("事发时间不能晚于当前时间");
        }
        if (article.getItemType() != ItemType.LOST
                && article.getItemType() != ItemType.FOUND) {
            throw conflict("信息类型不合法");
        }
    }

    private static String safeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return DEFAULT_OFFLINE_REASON;
        }
        String text = reason.trim();
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }
}
