package com.team.cms.auth.dto;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;

/** 替换式分配用户角色：提交的即为最终角色集合。 */
public class AssignRolesRequest {
    @NotEmpty(message = "角色列表不能为空")
    private List<@NotBlank(message = "角色代码不能为空") String> roleCodes;

    public List<String> getRoleCodes() { return roleCodes; }
    public void setRoleCodes(List<String> roleCodes) { this.roleCodes = roleCodes; }
}
