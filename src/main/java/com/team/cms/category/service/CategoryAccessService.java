package com.team.cms.category.service;

import com.team.cms.category.dto.CategoryOption;
import java.util.List;

/**
 * 跨模块契约（docs/team-iteration-plan.md）：全组唯一入口。
 * 内容绑定（保存草稿/发布）、公开检索、认领等模块都必须通过本接口校验分类有效性，
 * 不得自行实现分类可用性判断。
 * <ul>
 *   <li>分类不存在或已逻辑删除 → BusinessException(404, "分类不存在")</li>
 *   <li>自身或任一祖先停用 → BusinessException(409, "分类已停用")</li>
 *   <li>parentId 为 null 的根分类视为父级有效；categoryId 为 null → 400</li>
 * </ul>
 */
public interface CategoryAccessService {
    /** 能否绑定：存在、未删除、自身与全部祖先均启用。不抛异常。 */
    boolean isBindable(Long categoryId);

    /** 校验可绑定，失败抛 BusinessException（404/409/400）。 */
    void requireBindable(Long categoryId);

    /** 公开分类树：只包含自身与全部祖先均启用的分类，父停用则整棵子树隐藏。 */
    List<CategoryOption> publicTree();
}
