package com.team.cms.auth.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** 新建角色：代码为大写字母开头的短标识，名称展示用。 */
public class CreateRoleRequest {
    @NotBlank(message = "角色代码不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$", message = "角色代码只能是大写字母、数字或下划线，长度 2~40")
    private String code;

    @NotBlank(message = "角色名称不能为空")
    @Size(min = 2, max = 80, message = "角色名称长度必须在 2~80 之间")
    private String name;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
