package com.team.cms.review.controller;

import com.team.cms.article.dto.PrivateArticleDetail;
import com.team.cms.article.service.ItemWorkflowService;
import com.team.cms.auth.security.CsrfTokenService;
import com.team.cms.auth.security.RequirePermission;
import com.team.cms.common.api.PageQuery;
import com.team.cms.common.api.PageResult;
import com.team.cms.common.api.Result;
import com.team.cms.common.api.VersionRequest;
import com.team.cms.common.security.PermissionCodes;
import com.team.cms.review.dto.ReviewDecisionRequest;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/reviews")
public class ReviewController {
    private final ItemWorkflowService workflow;
    private final CsrfTokenService csrf;
    public ReviewController(ItemWorkflowService workflow, CsrfTokenService csrf) {
        this.workflow=workflow; this.csrf=csrf;
    }
    @GetMapping("/pending") @RequirePermission(PermissionCodes.REVIEW_VIEW)
    public Result<PageResult<PrivateArticleDetail>> pending(@Valid @ModelAttribute PageQuery query) {
        return Result.ok(workflow.queue(query.getPage(),query.getSize()));
    }
    @GetMapping("/approved") @RequirePermission(PermissionCodes.ITEM_PUBLISH)
    public Result<PageResult<PrivateArticleDetail>> approved(@Valid @ModelAttribute PageQuery query) {
        return Result.ok(workflow.approvedQueue(query.getPage(),query.getSize()));
    }
    @PostMapping("/{id}/approve") @RequirePermission(PermissionCodes.REVIEW_DECIDE)
    public Result<PrivateArticleDetail> approve(@PathVariable long id,
            @Valid @RequestBody ReviewDecisionRequest body, HttpServletRequest request) {
        csrf.requireValid(request);
        return Result.ok(workflow.decide(id,body.getVersion(),true,body.getComment()));
    }
    @PostMapping("/{id}/reject") @RequirePermission(PermissionCodes.REVIEW_DECIDE)
    public Result<PrivateArticleDetail> reject(@PathVariable long id,
            @Valid @RequestBody ReviewDecisionRequest body, HttpServletRequest request) {
        csrf.requireValid(request);
        return Result.ok(workflow.decide(id,body.getVersion(),false,body.getComment()));
    }
    @PostMapping("/{id}/publish") @RequirePermission(PermissionCodes.ITEM_PUBLISH)
    public Result<PrivateArticleDetail> publish(@PathVariable long id,
            @Valid @RequestBody VersionRequest body, HttpServletRequest request) {
        csrf.requireValid(request);
        return Result.ok(workflow.publish(id,body.getVersion()));
    }
}
