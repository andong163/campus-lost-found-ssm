package com.team.cms.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;

public final class PageResult<T> {
    private final long page, size, total;
    private final List<T> records;
    public PageResult(long page, long size, long total, List<T> records) {
        this.page = page; this.size = size; this.total = total; this.records = records;
    }
    public static <T> PageResult<T> from(IPage<T> value) {
        return new PageResult<>(value.getCurrent(), value.getSize(), value.getTotal(), value.getRecords());
    }
    public long getPage() { return page; }
    public long getSize() { return size; }
    public long getTotal() { return total; }
    public List<T> getRecords() { return records; }
}
