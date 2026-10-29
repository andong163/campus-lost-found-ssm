package com.team.cms.auth.dto;

import com.team.cms.auth.entity.SysPermission;

/** 权限点视图（权限种子清单）。 */
public class PermissionResponse {
    private final Long id;
    private final String code;
    private final String name;

    public PermissionResponse(Long id, String code, String name) {
        this.id = id;
        this.code = code;
        this.name = name;
    }

    public static PermissionResponse of(SysPermission permission) {
        return new PermissionResponse(permission.getId(), permission.getCode(), permission.getName());
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
}
