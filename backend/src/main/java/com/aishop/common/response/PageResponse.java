package com.aishop.common.response;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long total,
        boolean hasNext
) {
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        long shown = (long) page * size;
        return new PageResponse<>(items, page, size, total, shown < total);
    }
}

