package com.team.cms.article.controller;

import com.team.cms.article.dto.PrivateArticleDetail;
import com.team.cms.article.dto.CreateDraftRequest;
import com.team.cms.article.dto.EditArticleRequest;
import com.team.cms.article.dto.MyArticleQuery;
import com.team.cms.common.api.PageResult;
import com.team.cms.common.security.PermissionCodes;
import com.team.cms.common.api.VersionRequest;
import com.team.cms.article.service.ItemWorkflowService;
import com.team.cms.review.dto.ReviewRecordView;
import java.util.List;
import com.team.cms.article.service.ArticleDraftService;
import com.team.cms.auth.security.RequirePermission;
import com.team.cms.auth.security.CsrfTokenService;
import com.team.cms.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import javax.validation.Valid;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本人草稿接口，部署上下文路径由容器提供。 */
@RestController
@RequestMapping("/api/items")
public class ArticleDraftController {
    private final ArticleDraftService articleDraftService;
    private final CsrfTokenService csrfTokenService;
    private final ItemWorkflowService workflow;

    public ArticleDraftController(ArticleDraftService articleDraftService, CsrfTokenService csrfTokenService,
            ItemWorkflowService workflow) {
        this.articleDraftService = articleDraftService;
        this.csrfTokenService = csrfTokenService;
        this.workflow = workflow;
    }

    /** 新建本人草稿；请求不接收作者、状态和版本。 */
    @PostMapping
    @RequirePermission(PermissionCodes.ITEM_CREATE)
    public Result<PrivateArticleDetail> createMyDraft(@Valid @RequestBody CreateDraftRequest request,
            HttpServletRequest httpRequest) {
        csrfTokenService.requireValid(httpRequest);
        return Result.ok(articleDraftService.createMyDraft(request));
    }

    /** 读取本人草稿详情，联系方式仅包含在授权后的私有返回结构中。 */
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.ITEM_VIEW_OWN)
    public Result<PrivateArticleDetail> getMyDraft(@PathVariable long id) {
        return Result.ok(articleDraftService.getMyDraft(id));
    }

    @GetMapping("/mine") @RequirePermission(PermissionCodes.ITEM_VIEW_OWN)
    public Result<PageResult<PrivateArticleDetail>> mine(@Valid @ModelAttribute MyArticleQuery query) {
        return Result.ok(articleDraftService.listMine(query));
    }

    @GetMapping("/{id}/mine") @RequirePermission(PermissionCodes.ITEM_VIEW_OWN)
    public Result<PrivateArticleDetail> detail(@PathVariable long id) {
        return Result.ok(articleDraftService.getMine(id));
    }

    @PutMapping("/{id}") @RequirePermission(PermissionCodes.ITEM_EDIT_OWN)
    public Result<PrivateArticleDetail> edit(@PathVariable long id, @Valid @RequestBody EditArticleRequest body,
            HttpServletRequest request) {
        csrfTokenService.requireValid(request);
        return Result.ok(articleDraftService.editMine(id, body));
    }

    @PostMapping("/{id}/submit") @RequirePermission(PermissionCodes.ITEM_SUBMIT)
    public Result<PrivateArticleDetail> submit(@PathVariable long id, @Valid @RequestBody VersionRequest body,
            HttpServletRequest request) {
        csrfTokenService.requireValid(request);
        return Result.ok(workflow.submit(id, body.getVersion()));
    }

    @PostMapping("/{id}/withdraw") @RequirePermission(PermissionCodes.ITEM_WITHDRAW)
    public Result<PrivateArticleDetail> withdraw(@PathVariable long id, @Valid @RequestBody VersionRequest body,
            HttpServletRequest request) {
        csrfTokenService.requireValid(request);
        return Result.ok(workflow.withdraw(id, body.getVersion()));
    }

    @PostMapping("/{id}/offline") @RequirePermission(PermissionCodes.ITEM_OFFLINE_OWN)
    public Result<PrivateArticleDetail> offline(@PathVariable long id, @Valid @RequestBody VersionRequest body,
            HttpServletRequest request) {
        csrfTokenService.requireValid(request);
        return Result.ok(workflow.offline(id, body.getVersion()));
    }

    @PostMapping("/{id}/draft") @RequirePermission(PermissionCodes.ITEM_EDIT_OWN)
    public Result<PrivateArticleDetail> toDraft(@PathVariable long id, @Valid @RequestBody VersionRequest body,
            HttpServletRequest request) {
        csrfTokenService.requireValid(request);
        return Result.ok(workflow.toDraft(id, body.getVersion()));
    }

    @GetMapping("/{id}/reviews") @RequirePermission(PermissionCodes.ITEM_VIEW_OWN)
    public Result<List<ReviewRecordView>> reviews(@PathVariable long id) {
        return Result.ok(workflow.myReviews(id));
    }
}
