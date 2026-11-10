package com.team.cms.claim.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.team.cms.article.entity.Article;
import com.team.cms.article.mapper.ArticleMapper;
import com.team.cms.auth.AuthConstants;
import com.team.cms.auth.security.CurrentUser;
import com.team.cms.auth.security.CurrentUserContext;
import com.team.cms.category.service.CategoryAccessService;
import com.team.cms.claim.ClaimPermissions;
import com.team.cms.claim.domain.ClaimDecision;
import com.team.cms.claim.dto.ClaimCancelRequest;
import com.team.cms.claim.dto.ClaimCreateRequest;
import com.team.cms.claim.dto.ClaimDecisionRequest;
import com.team.cms.claim.dto.ClaimQuery;
import com.team.cms.claim.dto.ClaimView;
import com.team.cms.claim.dto.HandoverView;
import com.team.cms.claim.entity.ClaimRequest;
import com.team.cms.claim.mapper.ClaimRequestMapper;
import com.team.cms.common.api.PageResult;
import com.team.cms.common.domain.ArticleStatus;
import com.team.cms.common.domain.ClaimStatus;
import com.team.cms.common.domain.ItemType;
import com.team.cms.common.domain.ResolutionStatus;
import com.team.cms.common.exception.BusinessException;
import com.team.cms.common.security.PermissionCodes;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 认领业务实现。
 *
 * <p>锁顺序固定为“物品行 → 认领申请行”，创建、批准、撤销、完成都遵守；先校验、后条件更新。
 * 两个数据库唯一约束（同人同物唯一有效申请、单物品唯一 APPROVED）是最后防线，
 * 服务层在持有物品行锁时仍显式查询，以便返回可读的 409 而不是裸 DuplicateKeyException。</p>
 */
@Service
public class ClaimServiceImpl implements ClaimService {
    private static final int MAX_COMMENT = 500;
    private static final int MAX_REASON = 500;

    private final ArticleMapper articleMapper;
    private final ClaimRequestMapper claimRequestMapper;
    private final CategoryAccessService categoryAccessService;

    public ClaimServiceImpl(ArticleMapper articleMapper, ClaimRequestMapper claimRequestMapper,
            CategoryAccessService categoryAccessService) {
        this.articleMapper = articleMapper;
        this.claimRequestMapper = claimRequestMapper;
        this.categoryAccessService = categoryAccessService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClaimView apply(Long itemId, ClaimCreateRequest request) {
        CurrentUser actor = CurrentUserContext.require();
        requireWritePermission(actor, PermissionCodes.CLAIM_CREATE);
        Article article = articleMapper.selectByIdForUpdate(itemId);
        if (article == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "信息不存在");
        }
        if (article.getAuthorId().equals(actor.getId())) {
            throw conflict("不能认领本人发布的招领信息");
        }
        if (article.getItemType() != ItemType.FOUND) {
            throw conflict("寻物信息不支持认领申请");
        }
        if (article.getStatus() != ArticleStatus.PUBLISHED) {
            throw conflict("只有已发布的招领信息可以申请认领");
        }
        if (article.getResolutionStatus() != ResolutionStatus.OPEN) {
            throw conflict("该招领信息已结案，不能新增认领");
        }
        // 分类停用后旧申请仍可核实，但新增申请必须重新检查分类及祖先有效性。
        categoryAccessService.requireBindable(article.getCategoryId());
        if (claimRequestMapper.countActiveByApplicant(itemId, actor.getId()) > 0) {
            throw conflict("你已有一条待核实或已通过的申请，不能重复提交");
        }
        if (claimRequestMapper.countApprovedByItem(itemId) > 0) {
            throw conflict("该物品已有通过的认领，不能新增申请");
        }
        ClaimRequest claim = new ClaimRequest();
        claim.setArticleId(itemId);
        claim.setApplicantId(actor.getId());
        claim.setClaimDescription(request.getClaimDescription().trim());
        claim.setContactInfo(request.getContactInfo().trim());
        claim.setStatus(ClaimStatus.PENDING.name());
        claim.setReviewComment("");
        claim.setCancelReason("");
        claim.setCreatedAt(LocalDateTime.now());
        claim.setUpdatedAt(LocalDateTime.now());
        claim.setVersion(0);
        try {
            claimRequestMapper.insert(claim);
        } catch (DuplicateKeyException duplicate) {
            throw conflict("你已有一条待核实或已通过的申请，不能重复提交");
        }
        return toView(claim, article, actor);
    }

    @Override
    public PageResult<ClaimView> mine(ClaimQuery query) {
        CurrentUser actor = CurrentUserContext.require();
        Page<ClaimView> page = new Page<>(query.getPage(), query.getSize());
        return PageResult.from(claimRequestMapper.selectMine(page, actor.getId(), query.getStatus()));
    }

    @Override
    public PageResult<ClaimView> queue(ClaimQuery query) {
        requireClaimHandler(CurrentUserContext.require());
        Page<ClaimView> page = new Page<>(query.getPage(), query.getSize());
        return PageResult.from(claimRequestMapper.selectQueue(page, query.getStatus(), query.getItemId()));
    }

    @Override
    public List<ClaimView> forItem(Long itemId) {
        CurrentUser actor = CurrentUserContext.require();
        Article article = articleMapper.selectById(itemId);
        if (article == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "信息不存在");
        }
        boolean owner = article.getAuthorId().equals(actor.getId());
        if (!owner && !isClaimHandler(actor)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权查看该物品的认领申请");
        }
        return claimRequestMapper.selectByItem(itemId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void decide(Long claimId, ClaimDecisionRequest request) {
        CurrentUser actor = CurrentUserContext.require();
        requireClaimHandler(actor);
        ClaimRequest preview = claimRequestMapper.selectById(claimId);
        if (preview == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "认领申请不存在");
        }
        // 锁顺序：先物品，再申请。拿到物品锁后所有申请动作都被串行化。
        Article article = articleMapper.selectByIdForUpdate(preview.getArticleId());
        if (article == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "信息不存在");
        }
        ClaimRequest claim = claimRequestMapper.selectByIdForUpdate(claimId);
        if (claim == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "认领申请不存在");
        }
        if (!ClaimStatus.PENDING.name().equals(claim.getStatus())) {
            throw conflict("只有待核实的申请可以处理，请刷新后重试");
        }
        if (claim.getVersion() == null || claim.getVersion() != request.getVersion()) {
            throw conflict("认领版本已变化，请刷新后重试");
        }
        if (claim.getApplicantId().equals(actor.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "不能核实本人提交的申请");
        }
        if (article.getAuthorId().equals(actor.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "不能核实本人发布物品的申请");
        }
        String comment = normalizeComment(request.getComment());
        if (request.getDecision() == ClaimDecision.REJECTED && !StringUtils.hasText(comment)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "驳回认领必须填写理由");
        }
        if (request.getDecision() == ClaimDecision.APPROVED) {
            if (article.getItemType() != ItemType.FOUND
                    || article.getStatus() != ArticleStatus.PUBLISHED
                    || article.getResolutionStatus() != ResolutionStatus.OPEN) {
                throw conflict("物品不是已发布且未结案的招领信息，不能批准认领");
            }
            if (claimRequestMapper.countApprovedByItem(article.getId()) > 0) {
                throw conflict("该物品已有通过的认领，不能重复批准");
            }
        }
        int updated;
        try {
            updated = claimRequestMapper.updateDecision(claimId, request.getDecision().name(),
                actor.getId(), comment, LocalDateTime.now(), request.getVersion());
        } catch (DuplicateKeyException duplicate) {
            throw conflict("该物品已有通过的认领，不能重复批准");
        }
        if (updated != 1) {
            throw conflict("认领状态或版本已变化，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long claimId, ClaimCancelRequest request) {
        CurrentUser actor = CurrentUserContext.require();
        requireWritePermission(actor, PermissionCodes.CLAIM_CANCEL_OWN);
        ClaimRequest preview = claimRequestMapper.selectById(claimId);
        if (preview == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "认领申请不存在");
        }
        Article article = articleMapper.selectByIdForUpdate(preview.getArticleId());
        if (article == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "信息不存在");
        }
        ClaimRequest claim = claimRequestMapper.selectByIdForUpdate(claimId);
        if (claim == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "认领申请不存在");
        }
        if (!claim.getApplicantId().equals(actor.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只能撤销本人提交的申请");
        }
        if (!ClaimStatus.PENDING.name().equals(claim.getStatus())
                && !ClaimStatus.APPROVED.name().equals(claim.getStatus())) {
            throw conflict("当前认领状态不能撤销");
        }
        if (claim.getVersion() == null || claim.getVersion() != request.getVersion()) {
            throw conflict("认领版本已变化，请刷新后重试");
        }
        if (article.getResolutionStatus() == ResolutionStatus.RESOLVED) {
            throw conflict("物品已结案，不能撤销认领");
        }
        int updated = claimRequestMapper.updateCancel(claimId, trimTo(request.getReason(), MAX_REASON),
            request.getVersion());
        if (updated != 1) {
            throw conflict("认领状态或版本已变化，请刷新后重试");
        }
    }

    @Override
    public HandoverView handoverContext(Long itemId) {
        CurrentUser actor = CurrentUserContext.require();
        Article article = articleMapper.selectById(itemId);
        if (article == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "信息不存在");
        }
        boolean owner = article.getAuthorId().equals(actor.getId());
        if (!owner && !isClaimHandler(actor)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权查看该物品的交接信息");
        }
        ClaimView approved = claimRequestMapper.selectApprovedView(itemId);
        return new HandoverView(article.getId(), article.getTitle(), article.getItemType().name(),
            article.getStatus().name(), article.getResolutionStatus().name(), article.getVersion(),
            approved == null ? null : approved.getId(),
            approved == null ? null : approved.getApplicantName());
    }

    private static ClaimView toView(ClaimRequest claim, Article article, CurrentUser actor) {
        ClaimView view = new ClaimView();
        view.setId(claim.getId());
        view.setArticleId(article.getId());
        view.setArticleTitle(article.getTitle());
        view.setItemType(article.getItemType().name());
        view.setApplicantId(actor.getId());
        view.setApplicantName(actor.getDisplayName());
        view.setClaimDescription(claim.getClaimDescription());
        view.setContactInfo(claim.getContactInfo());
        view.setStatus(claim.getStatus());
        view.setReviewComment(claim.getReviewComment());
        view.setCancelReason(claim.getCancelReason());
        view.setCreatedAt(claim.getCreatedAt());
        view.setVersion(claim.getVersion());
        return view;
    }

    private static void requireWritePermission(CurrentUser actor, String permission) {
        if (!actor.hasRole(AuthConstants.ROLE_ADMIN) && !actor.hasPermission(permission)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无认领操作权限");
        }
    }

    private static void requireClaimHandler(CurrentUser actor) {
        if (!isClaimHandler(actor)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无认领处理权限");
        }
    }

    private static boolean isClaimHandler(CurrentUser actor) {
        return actor.hasRole(AuthConstants.ROLE_ADMIN)
            || actor.hasPermission(ClaimPermissions.CLAIM_HANDLE);
    }

    private static String normalizeComment(String comment) {
        return trimTo(comment, MAX_COMMENT);
    }

    private static String trimTo(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }
}
