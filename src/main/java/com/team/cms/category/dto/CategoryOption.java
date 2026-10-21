package com.team.cms.category.dto;

import java.util.ArrayList;
import java.util.List;

/** 公开分类树节点：只含自身与全部祖先均启用的分类，不暴露停用状态、排序等管理信息。 */
public class CategoryOption {
    private final Long id;
    private final String name;
    private final List<CategoryOption> children = new ArrayList<>();

    public CategoryOption(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public List<CategoryOption> getChildren() { return children; }
}
