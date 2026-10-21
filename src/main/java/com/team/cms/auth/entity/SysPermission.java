package com.team.cms.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.team.cms.common.persistence.BaseEntity;

/** 接口权限点，code 形如 "user:manage"、"article:create"。 */
@TableName("sys_permission")
public class SysPermission extends BaseEntity {
    private String code;
    private String name;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
