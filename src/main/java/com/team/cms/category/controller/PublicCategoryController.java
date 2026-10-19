package com.team.cms.category.controller;

import com.team.cms.category.dto.CategoryOption;
import com.team.cms.category.service.CategoryAccessService;
import com.team.cms.common.api.Result;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公开分类树：游客可用，只返回自身与全部祖先均启用的分类。 */
@RestController
@RequestMapping("/api/public/categories")
public class PublicCategoryController {
    private final CategoryAccessService access;

    public PublicCategoryController(CategoryAccessService access) {
        this.access = access;
    }

    @GetMapping
    public Result<List<CategoryOption>> tree() {
        return Result.ok(access.publicTree());
    }
}
