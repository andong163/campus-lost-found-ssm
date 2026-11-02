package com.team.cms.auth.dto;

/** 批量查询用户角色时的行映射（user_id + 角色代码），避免列表页 N+1 查询。 */
public class UserRoleRow {
    private Long userId;
    private String roleCode;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
}
