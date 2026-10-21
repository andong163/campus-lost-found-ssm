package com.team.cms.category.controller;

import com.team.cms.auth.security.RequirePermission;
import com.team.cms.category.dto.CategoryCreateRequest;
import com.team.cms.category.dto.CategoryCreateResponse;
import com.team.cms.category.dto.CategoryTreeNode;
import com.team.cms.category.service.CategoryService;
import com.team.cms.common.api.Result;
import java.util.List;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 分类管理接口（后台）。 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @PostMapping
    @RequirePermission("category:add")
    public Result<CategoryCreateResponse> create(@Valid @RequestBody CategoryCreateRequest request) {
        return Result.ok(service.create(request));
    }

    @GetMapping("/tree")
    @RequirePermission("category:view")
    public Result<List<CategoryTreeNode>> tree() {
        return Result.ok(service.tree());
    }
}
