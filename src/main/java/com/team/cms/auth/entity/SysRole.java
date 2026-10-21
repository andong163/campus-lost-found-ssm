package com.team.cms.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.team.cms.common.persistence.BaseEntity;

/** 系统角色（ADMIN/EDITOR/REVIEWER/VIEWER）。 */
@TableName("sys_role")
public class SysRole extends BaseEntity {
    private String code;
    private String name;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
