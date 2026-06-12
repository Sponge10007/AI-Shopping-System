package com.aishop.modules.admin.dto;

public record AdminMetricsResponse(
        long userCount,
        long productCount,
        long orderCount,
        long todayOrderCount,
        long searchCountToday,
        long aiChatCountToday,
        String aiServiceStatus,
        String vectorDbStatus
) {
}
