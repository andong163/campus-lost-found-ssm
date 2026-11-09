package com.team.cms.claim.dto;

import com.team.cms.common.api.PageQuery;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;

/** 认领列表筛选；状态使用白名单正则，排序固定为创建时间倒序 + ID 倒序。 */
public class ClaimQuery extends PageQuery {
    @Pattern(regexp = "^(PENDING|APPROVED|REJECTED|CANCELLED|COMPLETED)?$",
        message = "认领状态不合法")
    private String status;

    @Positive(message = "物品ID必须为正整数")
    private Long itemId;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
}
