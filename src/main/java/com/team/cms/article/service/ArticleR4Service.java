package com.team.cms.article.service;

import com.team.cms.article.dto.*;
import com.team.cms.article.entity.Article;
import com.team.cms.article.mapper.ArticleMapper;
import com.team.cms.auth.security.CurrentUser;
import com.team.cms.auth.security.CurrentUserContext;
import com.team.cms.audit.service.AuditTemplate;
import com.team.cms.category.service.CategoryAccessService;
import com.team.cms.claim.dto.ClaimEligibility;
import com.team.cms.claim.service.ClaimReadPolicy;
import com.team.cms.claim.mapper.ClaimRequestMapper;
import com.team.cms.claim.entity.ClaimRequest;
import com.team.cms.common.domain.*;
import com.team.cms.common.exception.BusinessException;
import com.team.cms.common.security.PermissionCodes;
import com.team.cms.review.ReviewPermissions;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** R4 只读接入；FOUND 授权及申请资格均委托唯一认领策略。 */
@Service
public class ArticleR4Service {
    private final ArticleMapper articles;
    private final CategoryAccessService categories;
    private final ClaimReadPolicy claims;
    private final ClaimRequestMapper requests;
    private final AuditTemplate audit;
    public ArticleR4Service(ArticleMapper articles, CategoryAccessService categories,
            ClaimReadPolicy claims, ClaimRequestMapper requests, AuditTemplate audit) {
        this.articles=articles; this.categories=categories; this.claims=claims; this.requests=requests; this.audit=audit;
    }
    public ContactView contact(long id) {
        return audit.execute("article","CONTACT_READ",id,()-> {
            CurrentUser user=CurrentUserContext.require();
            Article item=requireItem(id);
            if (item.isFound()) return new ContactView(claims.readFoundContact(item).getContactInfo());
            boolean owner=item.getAuthorId().equals(user.getId());
            boolean staff=user.hasPermission(ReviewPermissions.REVIEW_HANDLE)
                || user.hasPermission(PermissionCodes.REVIEW_DECIDE);
            if (!owner && !staff) {
                requirePublic(item);
                if (item.getResolutionStatus()!=ResolutionStatus.OPEN)
                    throw new BusinessException(HttpStatus.FORBIDDEN,"已结案寻物不提供公开联系入口");
                if (!user.hasPermission(PermissionCodes.ITEM_CONTACT_LOST))
                    throw new BusinessException(HttpStatus.FORBIDDEN,"无联系失主权限");
            }
            return new ContactView(item.getContactInfo());
        });
    }
    public ClaimEligibility eligibility(long id) {
        CurrentUser user=CurrentUserContext.require();
        Article item=requireItem(id);
        // 资格接口不能被用于枚举未公开物品；作者仍可查看本人的不可认领原因。
        ClaimEligibility result=claims.eligibility(item,user);
        if (!item.getAuthorId().equals(user.getId()) &&
                ("NOT_PUBLISHED".equals(result.getReason()) || "CATEGORY_UNAVAILABLE".equals(result.getReason())))
            throw new BusinessException(HttpStatus.NOT_FOUND,"公开信息不存在");
        return result;
    }
    public ResolutionView publicResolution(long id) {
        Article item=requireItem(id); requirePublic(item);
        return resolution(item);
    }
    public ArticleSummaryView mySummary(long id) {
        Article item=requireItem(id);
        if (!item.getAuthorId().equals(CurrentUserContext.require().getId()))
            throw new BusinessException(HttpStatus.NOT_FOUND,"信息不存在");
        return new ArticleSummaryView(item);
    }
    public ResolutionView myResolution(long id) {
        Article item=requireItem(id);
        if (!item.getAuthorId().equals(CurrentUserContext.require().getId()))
            throw new BusinessException(HttpStatus.NOT_FOUND,"信息不存在");
        return resolution(item);
    }
    private ResolutionView resolution(Article item) {
        String result="OPEN";
        if (item.getResolutionStatus()==ResolutionStatus.RESOLVED) {
            if (item.isLost()) result="LOST_RECOVERED";
            else result=requests.selectCount(new LambdaQueryWrapper<ClaimRequest>()
                .eq(ClaimRequest::getArticleId,item.getId()).eq(ClaimRequest::getStatus,"COMPLETED"))>0
                ? "FOUND_HANDOVER" : "RESOLVED_UNCONFIRMED";
        }
        return new ResolutionView(item.getResolutionStatus(),item.getResolvedAt(),result);
    }
    private Article requireItem(long id) {
        Article item=id<=0?null:articles.selectById(id);
        if (item==null) throw new BusinessException(HttpStatus.NOT_FOUND,"信息不存在");
        return item;
    }
    private void requirePublic(Article item) {
        if (item.getStatus()!=ArticleStatus.PUBLISHED || !categories.isBindable(item.getCategoryId()))
            throw new BusinessException(HttpStatus.NOT_FOUND,"公开信息不存在");
    }
}
