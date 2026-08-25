package com.srs.live.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {
    private long total;
    private long current;
    private long size;
    private long pages;
    private List<T> records;

    public PageResult(long total, long current, long size, List<T> records) {
        this.total = total;
        this.current = current;
        this.size = size;
        this.pages = size > 0 ? (total + size - 1) / size : 0;
        this.records = records;
    }
}
