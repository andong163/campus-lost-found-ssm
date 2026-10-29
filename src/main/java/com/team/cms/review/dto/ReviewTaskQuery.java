package com.team.cms.review.dto;

import com.team.cms.common.api.PageQuery;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

/** 审核待办筛选：关键字只做参数化 LIKE，分类暂按单节点筛选。 */
public class ReviewTaskQuery extends PageQuery {
    @Size(max = 100, message = "关键字过长")
    private String keyword;

    @Pattern(regexp = "^(LOST|FOUND)?$", message = "信息类型只能是 LOST 或 FOUND")
    private String itemType;

    @Positive(message = "分类ID必须为正整数")
    private Long categoryId;

    /** 服务端写入的当前审核人，用于排除本人内容；客户端传值会被覆盖。 */
    private Long excludeAuthorId;

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getExcludeAuthorId() { return excludeAuthorId; }
    public void setExcludeAuthorId(Long excludeAuthorId) { this.excludeAuthorId = excludeAuthorId; }
}
