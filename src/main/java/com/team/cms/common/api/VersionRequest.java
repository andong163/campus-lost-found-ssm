package com.team.cms.common.api;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;

/** Extend for single-record writes; compare version in the same transaction as the update. */
public class VersionRequest {
    @NotNull(message = "版本号不能为空")
    @PositiveOrZero(message = "版本号不能小于0")
    private Integer version;

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
