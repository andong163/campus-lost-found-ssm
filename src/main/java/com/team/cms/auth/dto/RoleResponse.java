package com.team.cms.auth.dto;

import com.team.cms.auth.entity.SysRole;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** 角色视图：角色代码 + 已授予的权限代码。 */
public class RoleResponse {
    private final Long id;
    private final String code;
    private final String name;
    private final List<String> permissions;

    public RoleResponse(Long id, String code, String name, Collection<String> permissions) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.permissions = Collections.unmodifiableList(new ArrayList<>(permissions));
    }

    public static RoleResponse of(SysRole role, Collection<String> permissions) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), permissions);
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public List<String> getPermissions() { return permissions; }
}
