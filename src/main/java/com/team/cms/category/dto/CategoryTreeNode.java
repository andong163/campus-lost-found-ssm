package com.team.cms.category.dto;

import java.util.ArrayList;
import java.util.List;

/** 管理端分类树节点：包含停用节点与排序信息，供后台管理页使用。 */
public class CategoryTreeNode {
    private final Long id;
    private final String name;
    private final Long parentId;
    private final Integer sortOrder;
    private final boolean enabled;
    private final List<CategoryTreeNode> children = new ArrayList<>();

    public CategoryTreeNode(Long id, String name, Long parentId, Integer sortOrder, boolean enabled) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.sortOrder = sortOrder;
        this.enabled = enabled;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Long getParentId() { return parentId; }
    public Integer getSortOrder() { return sortOrder; }
    public boolean isEnabled() { return enabled; }
    public List<CategoryTreeNode> getChildren() { return children; }
}
