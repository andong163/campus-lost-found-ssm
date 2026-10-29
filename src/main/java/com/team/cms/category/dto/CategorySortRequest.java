package com.team.cms.category.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/** 分类排序请求；sortOrder 非负，与库表 ck_category_sort 约束保持一致。 */
public class CategorySortRequest {
    @NotNull(message = "排序号不能为空")
    @Min(value = 0, message = "排序号不能小于0")
    private Integer sortOrder;

    public Integer getSortOrder() { return sortOrder; }

    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
