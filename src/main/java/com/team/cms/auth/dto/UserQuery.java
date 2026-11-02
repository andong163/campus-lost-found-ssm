package com.team.cms.auth.dto;

import com.team.cms.common.api.PageQuery;

/** 用户分页查询条件；排序固定为 id 倒序，不接受前端传入的排序字段。 */
public class UserQuery extends PageQuery {
    private String username;
    private String displayName;
    /** 1=启用，0=停用，null=不过滤。 */
    private Integer enabled;
    private String roleCode;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
}
