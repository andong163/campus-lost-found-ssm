package com.team.cms.auth.dto;

import javax.validation.constraints.NotNull;

/** 启用/停用账号请求。 */
public class UpdateUserStatusRequest {
    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
