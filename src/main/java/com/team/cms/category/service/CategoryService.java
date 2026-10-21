package com.team.cms.category.service;

import com.team.cms.category.dto.CategoryCreateRequest;
import com.team.cms.category.dto.CategoryCreateResponse;
import com.team.cms.category.dto.CategoryTreeNode;
import java.util.List;

/** 分类管理服务（后台）。R1 提供新增与管理树；修改、移动、排序、启停、删除在 R2。 */
public interface CategoryService {
    CategoryCreateResponse create(CategoryCreateRequest request);

    /** 管理端分类树：包含停用节点，供后台管理页展示。 */
    List<CategoryTreeNode> tree();
}
