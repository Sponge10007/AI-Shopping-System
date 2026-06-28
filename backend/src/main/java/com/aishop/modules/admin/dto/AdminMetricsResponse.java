package com.aishop.modules.admin.dto;

public record AdminMetricsResponse(
        long userCount,
        long productCount,
        long orderCount,
        long todayOrderCount,
        long searchCountToday,
        long aiChatCountToday,
        long activeUserCount,
        long onSaleProductCount,
        long pendingOrderCount,
        long paidOrderCount,
        String aiServiceStatus,
        String vectorDbStatus
) {
}
