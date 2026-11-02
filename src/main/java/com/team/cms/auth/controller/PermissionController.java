package com.team.cms.auth.controller;

import com.team.cms.auth.dto.PermissionResponse;
import com.team.cms.auth.security.RequirePermission;
import com.team.cms.auth.service.RoleAdminService;
import com.team.cms.common.api.Result;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 权限种子清单：供分配角色权限时选择。 */
@RestController
@RequestMapping("/api/permissions")
public class PermissionController {
    private final RoleAdminService roleAdminService;

    public PermissionController(RoleAdminService roleAdminService) {
        this.roleAdminService = roleAdminService;
    }

    @GetMapping
    @RequirePermission("role:manage")
    public Result<List<PermissionResponse>> list() {
        return Result.ok(roleAdminService.permissions());
    }
}
