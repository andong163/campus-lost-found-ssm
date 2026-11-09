package com.team.cms.claim.service;

import com.team.cms.article.entity.Article;
import com.team.cms.article.mapper.ArticleMapper;
import com.team.cms.auth.AuthConstants;
import com.team.cms.auth.security.CurrentUser;
import com.team.cms.auth.security.CurrentUserContext;
import com.team.cms.claim.ClaimPermissions;
import com.team.cms.claim.dto.FoundContactView;
import com.team.cms.claim.mapper.ClaimRequestMapper;
import com.team.cms.common.domain.ArticleStatus;
import com.team.cms.common.domain.ItemType;
import com.team.cms.common.exception.BusinessException;
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

    private final com.team.cms.category.service.CategoryAccessService categories;
    private final ArticleMapper articleMapper;
    private final ClaimRequestMapper claimRequestMapper;

    public ClaimReadPolicyImpl(ArticleMapper articleMapper, ClaimRequestMapper claimRequestMapper, com.team.cms.category.service.CategoryAccessService categories) {
        this.categories=categories;
        this.articleMapper = articleMapper;
        this.claimRequestMapper = claimRequestMapper;
    }

    @Override
    public com.team.cms.claim.dto.ClaimEligibility eligibility(Article article, CurrentUser user) {
        String reason="ELIGIBLE";
        if (article==null || !Integer.valueOf(0).equals(article.getDeleted())) reason="NOT_FOUND";
        else if (article.getStatus()!=ArticleStatus.PUBLISHED) reason="NOT_PUBLISHED";
        else if (!categories.isBindable(article.getCategoryId())) reason="CATEGORY_UNAVAILABLE";
        else if (user==null) reason="LOGIN_REQUIRED";
        else if (!user.hasPermission(com.team.cms.common.security.PermissionCodes.CLAIM_CREATE)) reason="NO_PERMISSION";
        else if (article.getItemType()!=ItemType.FOUND) reason="LOST_NOT_CLAIMABLE";
        else if (article.getAuthorId().equals(user.getId())) reason="OWN_ITEM";
        else if (article.getResolutionStatus()!=com.team.cms.common.domain.ResolutionStatus.OPEN) reason="RESOLVED";
        else if (claimRequestMapper.countActiveByApplicant(article.getId(),user.getId())>0) reason="ACTIVE_APPLICATION";
        else if (claimRequestMapper.countApprovedByItem(article.getId())>0) reason="ALREADY_APPROVED";
        return new com.team.cms.claim.dto.ClaimEligibility("ELIGIBLE".equals(reason),reason);
    }

    @Override
    public void requireEligible(Article article, CurrentUser user) {
        com.team.cms.claim.dto.ClaimEligibility result=eligibility(article,user);
        if (!result.isEligible()) throw new BusinessException(
            "NO_PERMISSION".equals(result.getReason()) ? HttpStatus.FORBIDDEN : HttpStatus.CONFLICT,
            "当前不可认领："+result.getReason());
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
        return claimRequestMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.team.cms.claim.entity.ClaimRequest>()
            .eq(com.team.cms.claim.entity.ClaimRequest::getArticleId,article.getId())
            .eq(com.team.cms.claim.entity.ClaimRequest::getApplicantId,user.getId())
            .in(com.team.cms.claim.entity.ClaimRequest::getStatus,"APPROVED","COMPLETED")) > 0;
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
