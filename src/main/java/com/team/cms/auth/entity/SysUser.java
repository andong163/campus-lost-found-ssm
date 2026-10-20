package com.team.cms.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.team.cms.common.persistence.BaseEntity;

/** 系统用户；除通用审计字段外，还包含账号启用开关 enabled。 */
@TableName("sys_user")
public class SysUser extends BaseEntity {
    private String username;
    private String passwordHash;
    private String displayName;
    private Integer enabled;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
}
