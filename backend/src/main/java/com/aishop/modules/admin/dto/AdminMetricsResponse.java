package com.aishop.modules.admin.dto;

public record AdminMetricsResponse(
        long userCount,
        long productCount,
        long orderCount,
        long todayOrderCount,
        String aiServiceStatus,
        String vectorDbStatus
) {
}

