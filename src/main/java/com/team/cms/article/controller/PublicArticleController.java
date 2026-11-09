package com.team.cms.article.controller;

import com.team.cms.article.dto.PublicArticleQuery;
import com.team.cms.article.dto.PublicArticleView;
import com.team.cms.article.service.PublicArticleService;
import com.team.cms.common.api.PageResult;
import com.team.cms.common.api.Result;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/items")
public class PublicArticleController {
    private final PublicArticleService service;
    public PublicArticleController(PublicArticleService service) { this.service=service; }
    @GetMapping public Result<PageResult<PublicArticleView>> list(@Valid @ModelAttribute PublicArticleQuery query) {
        return Result.ok(service.list(query));
    }
    @GetMapping("/{id}") public Result<PublicArticleView> detail(@PathVariable long id) {
        return Result.ok(service.detail(id));
    }
}
