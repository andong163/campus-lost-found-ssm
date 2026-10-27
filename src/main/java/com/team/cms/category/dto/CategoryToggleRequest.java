package com.team.cms.category.dto;

import javax.validation.constraints.NotNull;

/** 分类启停请求。 */
public class CategoryToggleRequest {
    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;

    public Boolean getEnabled() { return enabled; }

    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
