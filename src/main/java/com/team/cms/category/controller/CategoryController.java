package com.team.cms.category.controller;

import com.team.cms.auth.security.RequirePermission;
import com.team.cms.category.dto.CategoryCreateRequest;
import com.team.cms.category.dto.CategoryCreateResponse;
import com.team.cms.category.dto.CategoryMoveRequest;
import com.team.cms.category.dto.CategoryRenameRequest;
import com.team.cms.category.dto.CategorySortRequest;
import com.team.cms.category.dto.CategoryToggleRequest;
import com.team.cms.category.dto.CategoryTreeNode;
import com.team.cms.category.service.CategoryService;
import com.team.cms.common.api.Result;
import java.util.List;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 分类管理接口（后台）。R1 提供新增与管理树，R2 补齐改名、移动、排序、启停与逻辑删除。 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService service;

    public CategoryController(@Qualifier("categoryServiceImpl") CategoryService service) {
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

    @PutMapping("/{id}")
    @RequirePermission("category:edit")
    public Result<CategoryCreateResponse> rename(@PathVariable("id") Long id,
            @Valid @RequestBody CategoryRenameRequest request) {
        return Result.ok(service.rename(id, request.getName()));
    }

    @PutMapping("/{id}/parent")
    @RequirePermission("category:move")
    public Result<CategoryCreateResponse> move(@PathVariable("id") Long id,
            @Valid @RequestBody CategoryMoveRequest request) {
        return Result.ok(service.move(id, request.getParentId()));
    }

    @PutMapping("/{id}/sort")
    @RequirePermission("category:sort")
    public Result<CategoryCreateResponse> sort(@PathVariable("id") Long id,
            @Valid @RequestBody CategorySortRequest request) {
        return Result.ok(service.sort(id, request.getSortOrder()));
    }

    @PutMapping("/{id}/enabled")
    @RequirePermission("category:enable")
    public Result<CategoryCreateResponse> toggle(@PathVariable("id") Long id,
            @Valid @RequestBody CategoryToggleRequest request) {
        return Result.ok(service.toggle(id, request.getEnabled()));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("category:delete")
    public Result<Long> delete(@PathVariable("id") Long id) {
        service.delete(id);
        return Result.ok(id);
    }
}
