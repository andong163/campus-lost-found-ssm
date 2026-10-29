package com.team.cms.article.controller;

import com.team.cms.article.dto.PrivateArticleDetail;
import com.team.cms.article.dto.CreateDraftRequest;
import com.team.cms.article.service.ArticleDraftService;
import com.team.cms.auth.security.RequirePermission;
import com.team.cms.auth.security.CsrfTokenService;
import com.team.cms.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    public ArticleDraftController(ArticleDraftService articleDraftService, CsrfTokenService csrfTokenService) {
        this.articleDraftService = articleDraftService;
        this.csrfTokenService = csrfTokenService;
    }

    /** 新建本人草稿；请求不接收作者、状态和版本。 */
    @PostMapping
    @RequirePermission("article:create")
    public Result<PrivateArticleDetail> createMyDraft(@Valid @RequestBody CreateDraftRequest request,
            HttpServletRequest httpRequest) {
        csrfTokenService.requireValid(httpRequest);
        return Result.ok(articleDraftService.createMyDraft(request));
    }

    /** 读取本人草稿详情，联系方式仅包含在授权后的私有返回结构中。 */
    @GetMapping("/{id}")
    @RequirePermission("article:edit")
    public Result<PrivateArticleDetail> getMyDraft(@PathVariable long id) {
        return Result.ok(articleDraftService.getMyDraft(id));
    }
}
