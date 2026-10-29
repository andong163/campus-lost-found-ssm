package com.team.cms.category.service;

import com.team.cms.category.dto.CategoryCreateRequest;
import com.team.cms.category.dto.CategoryCreateResponse;
import com.team.cms.category.dto.CategoryTreeNode;
import java.util.List;

/** 分类管理服务（后台）。R1 提供新增与管理树；R2 提供改名、移动、排序、启停与逻辑删除。 */
public interface CategoryService {
    CategoryCreateResponse create(CategoryCreateRequest request);

    /** 管理端分类树：包含停用节点，供后台管理页展示。 */
    List<CategoryTreeNode> tree();

    /** 改名；同一父级下重名（trim 后忽略大小写）抛 409。 */
    CategoryCreateResponse rename(Long id, String name);

    /** 移动；parentId 为 null 表示移到根级。不能移到自己或自己的后代下。 */
    CategoryCreateResponse move(Long id, Long parentId);

    /** 只改排序号，不改父级与名称。 */
    CategoryCreateResponse sort(Long id, Integer sortOrder);

    /** 启停；停用后公开树隐藏该节点及其整棵子树。 */
    CategoryCreateResponse toggle(Long id, boolean enabled);

    /** 逻辑删除；仍有未删除子分类或内容引用时抛 409。 */
    void delete(Long id);
}
