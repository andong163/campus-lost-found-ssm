package com.team.cms.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** 注册请求。不接收角色/权限字段（未知字段一律忽略），注册一律分配 EDITOR。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegisterRequest {
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{4,30}$", message = "用户名需为4~30位字母、数字或下划线")
    private String username;

    @NotBlank(message = "昵称不能为空")
    @Size(min = 2, max = 20, message = "昵称需为2~20个字符")
    private String displayName;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码需为8~64个字符")
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
