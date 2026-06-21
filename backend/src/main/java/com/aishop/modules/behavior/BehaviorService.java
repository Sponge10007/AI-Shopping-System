package com.aishop.modules.behavior;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.infrastructure.persistence.entity.BehaviorLogEntity;
import com.aishop.infrastructure.persistence.repository.BehaviorLogRepository;
import com.aishop.modules.behavior.dto.BehaviorEventRequest;
import com.aishop.modules.behavior.dto.BehaviorEventResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.HashMap;

import java.util.Map;
import java.util.Set;

/**
 * 用户行为日志服务
 *
 * 记录用户在平台上的关键行为事件，包括：
 * - VIEW：浏览商品详情
 * - SEARCH：搜索商品
 * - ADD_TO_CART：加入购物车
 * - PURCHASE：购买商品
 *
 * 修复记录（2026-06-08）：
 * - 修复 targetId 为 null 时的 NPE：SEARCH 事件 targetId 可为 null，
 *   使用 null 安全的方式处理
 * - 修复非法事件类型的异常映射：BusinessException 应正确映射到 HTTP 400
 *   （由 GlobalExceptionHandler 处理）
 * - 修复实体字段类型：productId 改为 String 类型以匹配数据库 VARCHAR(64)
 * - 修复 metadata 序列化：将 Map 转为 JSON 字符串存储
 * - 修复 record 类型访问方式：BehaviorEventRequest 是 record，使用 eventType()
 *   而非 getEventType()
 * - 【2026-06-08 v2】修复 SEARCH 事件 targetType 校验：SEARCH 事件没有特定的目标类型，
 *   允许 targetType 为 null，仅对非 SEARCH 事件校验 targetType 非空
 *
 * 修复记录（2026-06-08 v3）：
 * - 【关键修复】metadata 处理逻辑适配 Map<String, Object> 类型
 *   原因：BehaviorLogEntity.metadata 字段类型从 String 改为 Map<String, Object>
 *   以解决 JSONB 类型不匹配问题。Service 层不再需要手动拼接 JSON 字符串，
 *   直接将 Map 设置到实体即可，Hibernate 会自动序列化为 JSONB。
 * - SEARCH 事件的 query 提取逻辑改为从 Map 中直接获取 "keyword" 字段
 */
@Service
public class BehaviorService {

    private static final Logger log = LoggerFactory.getLogger(BehaviorService.class);

    /**
     * 允许的事件类型白名单
     */
    private static final Set<String> ALLOWED_EVENT_TYPES = Set.of(
        "VIEW", "SEARCH", "IMAGE_SEARCH", "CHAT", "AI_COMPARE", "ADD_TO_CART", "PURCHASE"
    );

    private final BehaviorLogRepository behaviorLogRepository;

    private static final Set<String> OPTIONAL_TARGET_EVENTS = Set.of(
            "SEARCH", "IMAGE_SEARCH", "CHAT", "AI_COMPARE"
    );

    public BehaviorService(BehaviorLogRepository behaviorLogRepository) {
        this.behaviorLogRepository = behaviorLogRepository;
    }

    /**
     * 记录行为事件
     *
     * @param userId  当前用户 ID（从 JWT 中提取）
     * @param request 事件请求体（record 类型，通过字段名访问）
     * @return 事件记录响应
     */
    public BehaviorEventResponse recordEvent(String userId, BehaviorEventRequest request) {
        // 校验事件类型
        String eventType = request.eventType();
        if (eventType == null || eventType.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "事件类型不能为空");
        }
        if (!ALLOWED_EVENT_TYPES.contains(eventType)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "不支持的事件类型: " + eventType
                            + "，仅支持: VIEW, SEARCH, ADD_TO_CART, PURCHASE");
        }

        // 【修复 v2】校验 targetType：SEARCH 事件没有特定的目标类型，允许为 null
        // 其他事件（VIEW / ADD_TO_CART / PURCHASE）必须有目标类型
        String targetType = request.targetType();
        if (!OPTIONAL_TARGET_EVENTS.contains(eventType)) {
            if (targetType == null || targetType.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "目标类型不能为空");
            }
        }

        log.debug("记录行为事件: userId={}, eventType={}, targetType={}, targetId={}",
                userId, eventType, targetType, request.targetId());

        // 构建实体
        BehaviorLogEntity logEntity = new BehaviorLogEntity();
        logEntity.setUserId(userId);
        logEntity.setEventType(eventType);

        if ("PRODUCT".equalsIgnoreCase(targetType)) {
            logEntity.setProductId(request.targetId());
        }

        Map<String, Object> metadata = request.metadata();
        if (metadata != null && !metadata.isEmpty()) {
            logEntity.setMetadata(metadata);

            Object keyword = metadata.get("keyword");
            if (keyword != null) {
                logEntity.setQuery(String.valueOf(keyword));
            }
        }

        // 保存到数据库
        BehaviorLogEntity saved = behaviorLogRepository.save(logEntity);

        log.info("行为事件记录成功: id={}, userId={}, eventType={}",
                saved.getId(), saved.getUserId(), saved.getEventType());

        return new BehaviorEventResponse(true);
    }

    public void recordForUser(String userId, BehaviorEventRequest request) {
        try {
            recordEvent(userId, normalizeLegacyRequest(request));
        } catch (BusinessException exception) {
            log.warn("行为事件记录被拒绝: userId={}, reason={}", userId, exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("行为事件记录失败但不阻断主流程: userId={}, error={}", userId, exception.getMessage());
        }
    }

    private BehaviorEventRequest normalizeLegacyRequest(BehaviorEventRequest request) {
        if (request == null) {
            return null;
        }

        String eventType = request.eventType();
        Map<String, Object> metadata = request.metadata() == null
                ? new HashMap<>()
                : new HashMap<>(request.metadata());

        if ("SEARCH".equals(eventType) && request.targetId() != null && !request.targetId().isBlank()) {
            metadata.putIfAbsent("keyword", request.targetId());
            return new BehaviorEventRequest(eventType, request.targetType(), null, metadata);
        }

        if ("CHAT".equals(eventType) && request.targetId() != null && !request.targetId().isBlank()) {
            metadata.putIfAbsent("content", request.targetId());
            return new BehaviorEventRequest(eventType, request.targetType(), null, metadata);
        }

        return request;
    }
}
