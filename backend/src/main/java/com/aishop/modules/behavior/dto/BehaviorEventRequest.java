package com.aishop.modules.behavior.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 行为事件记录请求 DTO
 *
 * 用于接收前端发送的用户行为事件数据。
 *
 * 字段说明：
 * - eventType: 事件类型（VIEW / SEARCH / ADD_TO_CART / PURCHASE），必填
 * - targetType: 目标类型（如 PRODUCT / ORDER），SEARCH 事件可为 null
 * - targetId: 目标 ID（如商品 ID / 订单 ID），SEARCH 事件可为 null
 * - metadata: 附加元数据（Map 格式，存储事件上下文信息）
 *
 * 修复记录（2026-06-08 v2）：
 * - 【关键修复】metadata 字段类型从 String 改为 Map<String, Object>
 *   原因：前端传 JSON 对象时，Jackson 会自动反序列化为 Map，
 *   无需手动拼接 JSON 字符串，避免双重序列化导致的格式异常。
 *   同时与 BehaviorLogEntity.metadata 的 Map 类型保持一致。
 */
public record BehaviorEventRequest(
        @NotBlank(message = "事件类型不能为空")
        String eventType,

        String targetType,

        String targetId,

        Map<String, Object> metadata
) {}
