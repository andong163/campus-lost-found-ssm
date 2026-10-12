package com.team.cms.common.api;

import java.util.HashSet;
import java.util.List;
import javax.validation.constraints.*;

/** Validate before authorization and transactional batch work. Never accepts an empty batch. */
public class BatchIdsRequest {
    @NotNull(message = "批量ID不能为空")
    @Size(min = 1, max = 100, message = "每批必须包含1到100个ID")
    private List<@NotNull(message = "ID不能为空") @Positive(message = "ID必须为正整数") Long> ids;

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }

    @AssertTrue(message = "批量ID不能重复")
    public boolean isDistinct() {
        return ids == null || new HashSet<>(ids).size() == ids.size();
    }
}
