package com.team.cms.auth.entity;

/** 用户-角色关联表（复合主键，无审计字段，独立模型）。 */
public class SysUserRole {
    private Long userId;
    private Long roleId;

    public SysUserRole() { }
    public SysUserRole(Long userId, Long roleId) { this.userId = userId; this.roleId = roleId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
}
