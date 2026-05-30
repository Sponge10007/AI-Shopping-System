package com.aishop.common.response;

import com.aishop.common.web.TraceContext;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        String traceId
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", "OK", data, TraceContext.getTraceId());
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, code, message, null, TraceContext.getTraceId());
    }
}

