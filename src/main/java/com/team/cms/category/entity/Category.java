package com.team.cms.category.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.team.cms.common.persistence.BaseEntity;

/** cms_category 实体：多级分类。parentId 为 null 表示根分类。 */
@TableName("cms_category")
public class Category extends BaseEntity {
    private String name;
    /** null 是有效的根级值，移动时也必须写入数据库。 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long parentId;
    private Integer sortOrder;
    private Integer enabled;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
}
