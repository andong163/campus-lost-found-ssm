package com.team.cms.auth.dto;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;

/** 替换式分配角色权限：提交的即为该角色最终持有的权限集合。 */
public class AssignPermissionsRequest {
    @NotEmpty(message = "权限列表不能为空")
    private List<@NotBlank(message = "权限代码不能为空") String> permissionCodes;

    public List<String> getPermissionCodes() { return permissionCodes; }
    public void setPermissionCodes(List<String> permissionCodes) { this.permissionCodes = permissionCodes; }
}
