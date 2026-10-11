package com.team.cms.common.api;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/** Bind with @Valid @ModelAttribute. Sorting is a per-module allowlist. */
public class PageQuery {
    @Min(value = 1, message = "页码必须大于等于1") private int page = 1;
    @Min(value = 1, message = "每页数量必须大于等于1")
    @Max(value = 100, message = "每页数量不能超过100") private int size = 10;
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
