package com.team.cms.support;

import com.baomidou.mybatisplus.annotation.TableName;
import com.team.cms.common.persistence.BaseEntity;

@TableName("test_record")
public class TestRecord extends BaseEntity {
    private String name;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
