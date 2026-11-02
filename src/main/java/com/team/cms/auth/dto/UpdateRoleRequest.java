package com.team.cms.auth.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** 修改角色：角色代码不可变，只允许改展示名称。 */
public class UpdateRoleRequest {
    @NotBlank(message = "角色名称不能为空")
    @Size(min = 2, max = 80, message = "角色名称长度必须在 2~80 之间")
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
