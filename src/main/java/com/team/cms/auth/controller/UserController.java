package com.team.cms.auth.controller;

import com.team.cms.auth.dto.AssignRolesRequest;
import com.team.cms.auth.dto.UpdateUserStatusRequest;
import com.team.cms.auth.dto.UserDetailResponse;
import com.team.cms.auth.dto.UserQuery;
import com.team.cms.auth.dto.UserSummaryResponse;
import com.team.cms.auth.security.RequirePermission;
import com.team.cms.auth.service.UserAdminService;
import com.team.cms.common.api.PageResult;
import com.team.cms.common.api.Result;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户管理接口：分页、详情、启停、角色分配。全部要求 user:manage 权限。 */
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserAdminService userAdminService;

    public UserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    @RequirePermission("user:manage")
    public Result<PageResult<UserSummaryResponse>> page(@Valid @ModelAttribute UserQuery query) {
        return Result.ok(userAdminService.page(query));
    }

    @GetMapping("/{id}")
    @RequirePermission("user:manage")
    public Result<UserDetailResponse> detail(@PathVariable Long id) {
        return Result.ok(userAdminService.detail(id));
    }

    @PutMapping("/{id}/status")
    @RequirePermission("user:manage")
    public Result<Void> updateStatus(@PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        userAdminService.updateStatus(id, Boolean.TRUE.equals(request.getEnabled()));
        return Result.ok(null);
    }

    @PutMapping("/{id}/roles")
    @RequirePermission("user:manage")
    public Result<Void> assignRoles(@PathVariable Long id,
            @Valid @RequestBody AssignRolesRequest request) {
        userAdminService.assignRoles(id, request.getRoleCodes());
        return Result.ok(null);
    }
}
