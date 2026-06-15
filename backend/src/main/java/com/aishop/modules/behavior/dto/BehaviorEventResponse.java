package com.aishop.modules.behavior.dto;

/**
 * 行为事件响应
 *
 * 字段说明：
 * - accepted: 是否已接受记录
 */
public record BehaviorEventResponse(
        boolean accepted
) {
}
