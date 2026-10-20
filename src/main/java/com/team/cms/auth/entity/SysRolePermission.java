package com.team.cms.auth.entity;

/** 角色-权限关联表（复合主键，无审计字段，独立模型）。 */
public class SysRolePermission {
    private Long roleId;
    private Long permissionId;

    public SysRolePermission() { }
    public SysRolePermission(Long roleId, Long permissionId) {
        this.roleId = roleId; this.permissionId = permissionId;
    }

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public Long getPermissionId() { return permissionId; }
    public void setPermissionId(Long permissionId) { this.permissionId = permissionId; }
}
