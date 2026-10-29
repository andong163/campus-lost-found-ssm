package com.team.cms.category.dto;

import javax.validation.constraints.Positive;

/** 分类移动请求；parentId 为 null 表示移到根级。 */
public class CategoryMoveRequest {
    @Positive(message = "父分类ID必须为正整数")
    private Long parentId;

    public Long getParentId() { return parentId; }

    public void setParentId(Long parentId) { this.parentId = parentId; }
}
