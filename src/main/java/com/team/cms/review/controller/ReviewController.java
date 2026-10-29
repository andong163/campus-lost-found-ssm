package com.team.cms.review.controller;

import com.team.cms.audit.service.AuditTemplate;
import com.team.cms.article.dto.PrivateArticleDetail;
import com.team.cms.article.service.ItemWorkflowService;
import com.team.cms.auth.security.CsrfTokenService;
import com.team.cms.common.api.PageQuery;
import com.team.cms.common.api.VersionRequest;
import com.team.cms.common.security.PermissionCodes;
import com.team.cms.review.dto.ReviewActionRequest;
import javax.servlet.http.HttpServletRequest;
import com.team.cms.auth.security.RequirePermission;
import com.team.cms.common.api.PageResult;
import com.team.cms.common.api.Result;
import com.team.cms.review.ReviewPermissions;
import com.team.cms.review.dto.ReviewDecisionRequest;
import com.team.cms.review.dto.ReviewHistoryQuery;
import com.team.cms.review.dto.ReviewRecordView;
import com.team.cms.review.dto.ReviewTaskQuery;
import com.team.cms.review.dto.ReviewTaskView;
import com.team.cms.review.service.ReviewService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 审核工作台接口：待办、历史、决定、单物品审核记录。 */
@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;
    private final AuditTemplate auditTemplate;

    private final ItemWorkflowService workflow;
    private final CsrfTokenService csrf;
    public ReviewController(ReviewService reviewService, AuditTemplate auditTemplate,
            ItemWorkflowService workflow, CsrfTokenService csrf) {
        this.workflow=workflow; this.csrf=csrf;
        this.reviewService = reviewService;
        this.auditTemplate = auditTemplate;
    }

    @GetMapping("/reviews")
    @RequirePermission(ReviewPermissions.REVIEW_HANDLE)
    public Result<PageResult<ReviewTaskView>> pending(@Valid @ModelAttribute ReviewTaskQuery query) {
        return Result.ok(reviewService.pending(query));
    }

    @GetMapping("/reviews/history")
    @RequirePermission(ReviewPermissions.REVIEW_HANDLE)
    public Result<PageResult<ReviewRecordView>> history(@Valid @ModelAttribute ReviewHistoryQuery query) {
        return Result.ok(reviewService.history(query));
    }

    @PostMapping("/reviews/{itemId}/decision")
    @RequirePermission(ReviewPermissions.REVIEW_HANDLE)
    public Result<Void> decide(@PathVariable Long itemId,
            @Valid @RequestBody ReviewDecisionRequest request, HttpServletRequest httpRequest) {
        csrf.requireValid(httpRequest);
        auditTemplate.run("review", "REVIEW_DECIDE", itemId,
            () -> reviewService.decide(itemId, request));
        return Result.ok(null);
    }

    /** 作者或审核人员查看审核记录；权限与所有权由 Service 校验。 */
    @GetMapping("/items/{itemId}/review-records")
    public Result<List<ReviewRecordView>> records(@PathVariable Long itemId) {
        return Result.ok(reviewService.records(itemId));
    }

    @GetMapping("/reviews/pending") @RequirePermission(PermissionCodes.REVIEW_VIEW)
    public Result<PageResult<PrivateArticleDetail>> pendingItems(@Valid @ModelAttribute PageQuery query) {
        return Result.ok(workflow.queue(query.getPage(),query.getSize()));
    }
    @GetMapping("/reviews/approved") @RequirePermission(PermissionCodes.ITEM_PUBLISH)
    public Result<PageResult<PrivateArticleDetail>> approved(@Valid @ModelAttribute PageQuery query) {
        return Result.ok(workflow.approvedQueue(query.getPage(),query.getSize()));
    }
    @PostMapping("/reviews/{id}/approve") @RequirePermission(PermissionCodes.REVIEW_DECIDE)
    public Result<PrivateArticleDetail> approve(@PathVariable long id,
            @Valid @RequestBody ReviewActionRequest body, HttpServletRequest request) {
        csrf.requireValid(request);
        return Result.ok(auditTemplate.execute("review","REVIEW_DECIDE",id,
            () -> workflow.decide(id,body.getVersion(),true,body.getComment())));
    }
    @PostMapping("/reviews/{id}/reject") @RequirePermission(PermissionCodes.REVIEW_DECIDE)
    public Result<PrivateArticleDetail> reject(@PathVariable long id,
            @Valid @RequestBody ReviewActionRequest body, HttpServletRequest request) {
        csrf.requireValid(request);
        return Result.ok(auditTemplate.execute("review","REVIEW_DECIDE",id,
            () -> workflow.decide(id,body.getVersion(),false,body.getComment())));
    }
    @PostMapping("/reviews/{id}/publish") @RequirePermission(PermissionCodes.ITEM_PUBLISH)
    public Result<PrivateArticleDetail> publish(@PathVariable long id,
            @Valid @RequestBody VersionRequest body, HttpServletRequest request) {
        csrf.requireValid(request); return Result.ok(workflow.publish(id,body.getVersion()));
    }
}
