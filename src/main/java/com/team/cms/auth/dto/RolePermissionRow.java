package com.team.cms.auth.dto;

/** 角色-权限行映射（role_id + 权限代码），用于一次性装载全部角色的权限。 */
public class RolePermissionRow {
    private Long roleId;
    private String permissionCode;

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }
}
