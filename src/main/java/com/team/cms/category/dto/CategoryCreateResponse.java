package com.team.cms.category.dto;

/** 新增分类响应，只返回安全字段，不直接返回 Entity。 */
public class CategoryCreateResponse {
    private final Long id;
    private final String name;
    private final Long parentId;
    private final Integer sortOrder;
    private final boolean enabled;

    public CategoryCreateResponse(Long id, String name, Long parentId, Integer sortOrder, boolean enabled) {
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
}
