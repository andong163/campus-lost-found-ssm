package com.team.cms.review.controller;

import com.team.cms.audit.service.AuditTemplate;
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

    public ReviewController(ReviewService reviewService, AuditTemplate auditTemplate) {
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
            @Valid @RequestBody ReviewDecisionRequest request) {
        auditTemplate.run("review", "REVIEW_DECIDE", itemId,
            () -> reviewService.decide(itemId, request));
        return Result.ok(null);
    }

    /** 作者或审核人员查看审核记录；权限与所有权由 Service 校验。 */
    @GetMapping("/items/{itemId}/review-records")
    public Result<List<ReviewRecordView>> records(@PathVariable Long itemId) {
        return Result.ok(reviewService.records(itemId));
    }
}
