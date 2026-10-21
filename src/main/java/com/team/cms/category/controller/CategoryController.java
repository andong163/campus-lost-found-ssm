package com.team.cms.category.controller;

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

    // TODO(GJT R1): 待郭金滔的权限注解与拦截器合入后挂 @RequiresPermission("category:add")。
    // 在此之前按 R1 停合条件本接口不得判定为完成，并在 PR 中登记该依赖。
    @PostMapping
    public Result<CategoryCreateResponse> create(@Valid @RequestBody CategoryCreateRequest request) {
        return Result.ok(service.create(request));
    }

    // TODO(GJT R1): 待郭金滔的权限注解与拦截器合入后挂 @RequiresPermission("category:view")。
    @GetMapping("/tree")
    public Result<List<CategoryTreeNode>> tree() {
        return Result.ok(service.tree());
    }
}
