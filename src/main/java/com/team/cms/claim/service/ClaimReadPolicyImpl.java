package com.team.cms.claim.service;

import com.team.cms.article.entity.Article;
import com.team.cms.article.mapper.ArticleMapper;
import com.team.cms.auth.AuthConstants;
import com.team.cms.auth.security.CurrentUser;
import com.team.cms.auth.security.CurrentUserContext;
import com.team.cms.category.service.CategoryAccessService;
import com.team.cms.claim.ClaimPermissions;
import com.team.cms.claim.dto.ClaimEligibility;
import com.team.cms.claim.dto.FoundContactView;
import com.team.cms.claim.mapper.ClaimRequestMapper;
import com.team.cms.common.domain.ArticleStatus;
import com.team.cms.common.domain.ItemType;
import com.team.cms.common.domain.ResolutionStatus;
import com.team.cms.common.exception.BusinessException;
import com.team.cms.common.security.PermissionCodes;
import com.team.cms.review.ReviewPermissions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * FOUND 联系方式读取策略实现。
 *
 * <p>公开列表和详情永远不带联系方式；只有单条专用入口按身份判定。读取是否留痕由 Controller
 * 通过 AuditTemplate 记录，本类只做授权，避免同一请求重复写两条日志。</p>
 */
@Service
public class ClaimReadPolicyImpl implements ClaimReadPolicy {

    private final ArticleMapper articleMapper;
    private final ClaimRequestMapper claimRequestMapper;
    private final CategoryAccessService categories;

    public ClaimReadPolicyImpl(ArticleMapper articleMapper, ClaimRequestMapper claimRequestMapper,
            CategoryAccessService categories) {
        this.articleMapper = articleMapper;
        this.claimRequestMapper = claimRequestMapper;
        this.categories = categories;
    }

    @Override
    public ClaimEligibility eligibility(Article article, CurrentUser user) {
        String reason = eligibilityReason(article, user, false);
        return new ClaimEligibility("ELIGIBLE".equals(reason), reason);
    }

    @Override
    public void requireEligible(Article article, CurrentUser user) {
        switch (eligibilityReason(article, user, true)) {
            case "ELIGIBLE": return;
            case "NOT_FOUND": throw new BusinessException(HttpStatus.NOT_FOUND, "信息不存在");
            case "LOGIN_REQUIRED": throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录");
            case "NO_PERMISSION": throw new BusinessException(HttpStatus.FORBIDDEN, "无认领操作权限");
            case "OWN_ITEM": throw conflict("不能认领本人发布的招领信息");
            case "LOST_NOT_CLAIMABLE": throw conflict("寻物信息不支持认领申请");
            case "NOT_PUBLISHED": throw conflict("只有已发布的招领信息可以申请认领");
            case "RESOLVED": throw conflict("该招领信息已结案，不能新增认领");
            case "ACTIVE_APPLICATION": throw conflict("你已有一条待核实或已通过的申请，不能重复提交");
            case "ALREADY_APPROVED": throw conflict("该物品已有通过的认领，不能新增申请");
            default: throw conflict("分类不可用，不能新增认领");
        }
    }

    private String eligibilityReason(Article article, CurrentUser user, boolean lockCategory) {
        if (article == null || !Integer.valueOf(0).equals(article.getDeleted())) return "NOT_FOUND";
        if (user == null) return "LOGIN_REQUIRED";
        if (!user.hasRole(AuthConstants.ROLE_ADMIN) && !user.hasPermission(PermissionCodes.CLAIM_CREATE))
            return "NO_PERMISSION";
        if (article.getAuthorId().equals(user.getId())) return "OWN_ITEM";
        if (article.getItemType() != ItemType.FOUND) return "LOST_NOT_CLAIMABLE";
        if (article.getStatus() != ArticleStatus.PUBLISHED) return "NOT_PUBLISHED";
        if (article.getResolutionStatus() != ResolutionStatus.OPEN) return "RESOLVED";
        if (lockCategory) categories.requireBindable(article.getCategoryId());
        else if (!categories.isBindable(article.getCategoryId())) return "CATEGORY_UNAVAILABLE";
        if (claimRequestMapper.countActiveByApplicant(article.getId(), user.getId()) > 0)
            return "ACTIVE_APPLICATION";
        if (claimRequestMapper.countApprovedByItem(article.getId()) > 0) return "ALREADY_APPROVED";
        return "ELIGIBLE";
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }

    @Override
    public boolean canReadFoundContact(Article article, CurrentUser user) {
        if (article == null || user == null || article.getItemType() != ItemType.FOUND) {
            return false;
        }
        if (article.getAuthorId().equals(user.getId())) {
            return true;
        }
        if (isStaff(user)) {
            return true;
        }
        return claimRequestMapper.countVerifiedByApplicant(article.getId(), user.getId()) > 0;
    }

    @Override
    public FoundContactView readFoundContact(Long itemId) {
        return readFoundContact(articleMapper.selectById(itemId));
    }

    @Override
    public FoundContactView readFoundContact(Article article) {
        CurrentUser user = CurrentUserContext.require();
        if (article == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "信息不存在");
        }
        if (article.getItemType() != ItemType.FOUND) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "只有招领信息提供联系方式读取");
        }
        boolean owner = article.getAuthorId().equals(user.getId());
        boolean staff = isStaff(user);
        if (article.getStatus() != ArticleStatus.PUBLISHED && !owner && !staff) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "信息不存在或未公开");
        }
        if (!canReadFoundContact(article, user)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "认领核实通过后才能查看招领人联系方式");
        }
        if (!StringUtils.hasText(article.getContactInfo())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "发布者未填写联系方式");
        }
        return new FoundContactView(article.getContactInfo());
    }

    private static boolean isStaff(CurrentUser user) {
        return user.hasRole(AuthConstants.ROLE_ADMIN)
            || user.hasPermission(ClaimPermissions.CLAIM_HANDLE)
            || user.hasPermission(ReviewPermissions.REVIEW_HANDLE);
    }
}
