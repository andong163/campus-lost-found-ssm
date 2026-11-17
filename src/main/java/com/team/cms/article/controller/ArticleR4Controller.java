package com.team.cms.article.controller;

import com.team.cms.article.dto.ContactView;
import com.team.cms.article.dto.ResolutionView;
import com.team.cms.article.service.ArticleR4Service;
import com.team.cms.auth.security.RequirePermission;
import com.team.cms.claim.dto.ClaimEligibility;
import com.team.cms.common.api.Result;
import com.team.cms.common.security.PermissionCodes;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ArticleR4Controller {
    private final ArticleR4Service service;
    public ArticleR4Controller(ArticleR4Service service) { this.service=service; }
    @GetMapping("/items/{id}/r4-detail")
    @RequirePermission(PermissionCodes.ITEM_VIEW_OWN)
    public Result<com.team.cms.article.dto.ArticleSummaryView> mySummary(@PathVariable long id) {
        return Result.ok(service.mySummary(id));
    }
    @GetMapping("/items/{id}/contact")
    public Result<ContactView> contact(@PathVariable long id) { return Result.ok(service.contact(id)); }
    @GetMapping("/items/{id}/claim-eligibility")
    public Result<ClaimEligibility> eligibility(@PathVariable long id) { return Result.ok(service.eligibility(id)); }
    @GetMapping("/public/items/{id}/resolution")
    public Result<ResolutionView> resolution(@PathVariable long id) { return Result.ok(service.publicResolution(id)); }
    @GetMapping("/items/{id}/resolution")
    @RequirePermission(PermissionCodes.ITEM_VIEW_OWN)
    public Result<ResolutionView> myResolution(@PathVariable long id) { return Result.ok(service.myResolution(id)); }
}
