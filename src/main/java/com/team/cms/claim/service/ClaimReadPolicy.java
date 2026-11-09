package com.team.cms.claim.service;

import com.team.cms.article.entity.Article;
import com.team.cms.auth.security.CurrentUser;
import com.team.cms.claim.dto.FoundContactView;

/**
 * FOUND 联系方式读取策略（唯一入口）。
 *
 * <p>袁运轩的 /items/{id}/contact 与认领入口应调用本策略，不要由 ArticleService 或前端自行判断。
 * 可读对象：发布者本人、已通过核实的申请人、管理员、认领处理人员、审核人员。</p>
 */
public interface ClaimReadPolicy {

    com.team.cms.claim.dto.ClaimEligibility eligibility(Article article, CurrentUser user);
    void requireEligible(Article article, CurrentUser user);

    /** 纯判定，不抛异常，方便调用方决定是否展示“联系失主”入口。 */
    boolean canReadFoundContact(Article article, CurrentUser user);

    /** 单条读取；未公开、越权或信息不存在时抛 403/404，并可由调用方写审计。 */
    FoundContactView readFoundContact(Long itemId);
    FoundContactView readFoundContact(Article article);
}
