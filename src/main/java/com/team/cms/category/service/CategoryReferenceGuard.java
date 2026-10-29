package com.team.cms.category.service;

/**
 * 内容模块向分类模块登记的引用检查（SR R2 删除保护）。
 *
 * <p>依赖倒置：分类模块不反向依赖 article/claim 等业务模块，内容模块各自实现一个 Bean
 * 即可把"我引用了哪些分类"登记给分类模块。删除分类时分类模块按 {@link org.springframework.core.annotation.Order}
 * 顺序依次询问，任一实现返回非 null 即拒绝删除。</p>
 *
 * <p>实现方约定：只统计未逻辑删除的内容；返回的原因会作为 409 响应消息直接展示给后台用户，
 * 请写成"该分类下还有未删除的物品信息，不能删除"这类完整句子。</p>
 */
public interface CategoryReferenceGuard {
    /**
     * 存在未删除的内容引用该分类时返回人类可读的阻止原因；无引用返回 {@code null}。
     *
     * @param categoryId 待删除分类主键，调用方已校验存在
     */
    String blockingReason(Long categoryId);
}
