package com.team.cms.auth.controller;

import com.team.cms.auth.dto.AssignPermissionsRequest;
import com.team.cms.auth.dto.CreateRoleRequest;
import com.team.cms.auth.dto.RoleResponse;
import com.team.cms.auth.dto.UpdateRoleRequest;
import com.team.cms.auth.security.CsrfTokenService;
import com.team.cms.auth.security.RequirePermission;
import com.team.cms.auth.service.RoleAdminService;
import com.team.cms.common.api.Result;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 角色管理接口：列表、新建、改名、替换角色权限。全部要求 role:manage 权限；写操作另需 CSRF 令牌。 */
@RestController
@RequestMapping("/api/roles")
public class RoleController {
    private final RoleAdminService roleAdminService;
    private final CsrfTokenService csrfTokenService;

    public RoleController(RoleAdminService roleAdminService, CsrfTokenService csrfTokenService) {
        this.roleAdminService = roleAdminService;
        this.csrfTokenService = csrfTokenService;
    }

    @GetMapping
    @RequirePermission("role:manage")
    public Result<List<RoleResponse>> list() {
        return Result.ok(roleAdminService.list());
    }

    @GetMapping("/{id}")
    @RequirePermission("role:manage")
    public Result<RoleResponse> detail(@PathVariable Long id) {
        return Result.ok(roleAdminService.detail(id));
    }

    @PostMapping
    @RequirePermission("role:manage")
    public Result<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request,
            HttpServletRequest httpRequest) {
        csrfTokenService.requireValid(httpRequest);
        return Result.ok(roleAdminService.create(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("role:manage")
    public Result<RoleResponse> update(@PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest request, HttpServletRequest httpRequest) {
        csrfTokenService.requireValid(httpRequest);
        return Result.ok(roleAdminService.update(id, request));
    }

    @PutMapping("/{id}/permissions")
    @RequirePermission("role:manage")
    public Result<Void> assignPermissions(@PathVariable Long id,
            @Valid @RequestBody AssignPermissionsRequest request, HttpServletRequest httpRequest) {
        csrfTokenService.requireValid(httpRequest);
        roleAdminService.assignPermissions(id, request.getPermissionCodes());
        return Result.ok(null);
    }
}
