package com.team.cms.category.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** 分类改名请求；父级与排序走独立端点，便于按权限码分开授权。 */
public class CategoryRenameRequest {
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 80, message = "分类名称不能超过80个字符")
    private String name;

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }
}
