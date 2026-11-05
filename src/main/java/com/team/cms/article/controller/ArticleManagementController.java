package com.team.cms.article.controller;

import com.team.cms.article.dto.BatchArticleRequest;
import com.team.cms.article.dto.ManageArticleQuery;
import com.team.cms.article.dto.PrivateArticleDetail;
import com.team.cms.common.api.PageResult;
import com.team.cms.auth.security.CsrfTokenService;
import com.team.cms.auth.security.RequirePermission;
import com.team.cms.common.api.Result;
import com.team.cms.common.api.VersionRequest;
import com.team.cms.common.security.PermissionCodes;
import com.team.cms.review.service.ItemWorkflowService;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items")
public class ArticleManagementController {
    private final ItemWorkflowService workflow;
    private final com.team.cms.article.service.ItemWorkflowService articleViews;
    private final CsrfTokenService csrf;
    public ArticleManagementController(ItemWorkflowService workflow, CsrfTokenService csrf,
            com.team.cms.article.service.ItemWorkflowService articleViews) {
        this.workflow=workflow; this.csrf=csrf; this.articleViews=articleViews;
    }
    @GetMapping @RequirePermission(PermissionCodes.ITEM_VIEW_ANY)
    public Result<PageResult<PrivateArticleDetail>> manage(@Valid @ModelAttribute ManageArticleQuery query) {
        return Result.ok(articleViews.manage(query));
    }
    @PostMapping("/{id}/publish") @RequirePermission(PermissionCodes.ITEM_PUBLISH)
    public Result<Void> publish(@PathVariable long id, @Valid @RequestBody VersionRequest body, HttpServletRequest request) {
        csrf.requireValid(request); workflow.publish(id,body.getVersion()); return Result.ok(null);
    }
    @PostMapping("/{id}/manage-offline") @RequirePermission(PermissionCodes.ITEM_OFFLINE_ANY)
    public Result<Void> offline(@PathVariable long id, @Valid @RequestBody VersionRequest body, HttpServletRequest request) {
        csrf.requireValid(request); workflow.offline(id,body.getVersion(),null); return Result.ok(null);
    }
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id, @Valid @RequestBody VersionRequest body, HttpServletRequest request) {
        csrf.requireValid(request); workflow.delete(id,body.getVersion()); return Result.ok(null);
    }
    @PostMapping("/batch-publish") @RequirePermission(PermissionCodes.ITEM_BATCH)
    public Result<Integer> batchPublish(@Valid @RequestBody BatchArticleRequest body, HttpServletRequest request) {
        return batch("publish",body,request);
    }
    @PostMapping("/batch-offline") @RequirePermission(PermissionCodes.ITEM_BATCH)
    public Result<Integer> batchOffline(@Valid @RequestBody BatchArticleRequest body, HttpServletRequest request) {
        return batch("offline",body,request);
    }
    @PostMapping("/batch-delete") @RequirePermission(PermissionCodes.ITEM_BATCH)
    public Result<Integer> batchDelete(@Valid @RequestBody BatchArticleRequest body, HttpServletRequest request) {
        return batch("delete",body,request);
    }
    private Result<Integer> batch(String action, BatchArticleRequest body, HttpServletRequest request) {
        csrf.requireValid(request); body.validate(); workflow.batch(action,body.getVersions());
        return Result.ok(body.getIds().size());
    }
}
