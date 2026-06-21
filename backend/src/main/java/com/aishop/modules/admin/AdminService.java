package com.aishop.modules.admin;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.response.PageResponse;
import com.aishop.infrastructure.ai.AiServiceClient;
import com.aishop.infrastructure.persistence.entity.UserEntity;
import com.aishop.infrastructure.persistence.repository.BehaviorLogRepository;
import com.aishop.infrastructure.persistence.repository.OrderRepository;
import com.aishop.infrastructure.persistence.repository.ProductRepository;
import com.aishop.infrastructure.persistence.repository.UserRepository;
import com.aishop.modules.admin.dto.AdminMetricsResponse;
import com.aishop.modules.admin.dto.AdminUserResponse;
import com.aishop.modules.admin.dto.UpdateUserStatusRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 管理员服务 — 用户管理、平台监控
 *
 * 技术要点：
 * 1. 所有接口需要 ADMIN 角色（由 Controller 层校验）
 * 2. 用户管理：查看用户列表、修改用户状态（封禁/解封）
 * 3. 平台监控：统计用户数、商品数、订单数等概览数据
 * 4. 用户状态修改使用逻辑更新，保留操作记录
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final BehaviorLogRepository behaviorLogRepository;
    private final AiServiceClient aiServiceClient;

    public AdminService(UserRepository userRepository,
                        ProductRepository productRepository,
                        OrderRepository orderRepository,
                        BehaviorLogRepository behaviorLogRepository,
                        AiServiceClient aiServiceClient) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.behaviorLogRepository = behaviorLogRepository;
        this.aiServiceClient = aiServiceClient;
    }

    /**
     * 获取用户列表（管理员）
     *
     * 支持按角色筛选和关键词搜索（用户名/手机号）
     * 注意：不返回密码字段
     */
    public PageResponse<AdminUserResponse> listUsers(String role, String keyword, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;

        String normalizedRole = normalizeRole(role);
        String normalizedKeyword = normalizeKeyword(keyword);
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserEntity> userPage = userRepository.findAdminUsers(
                normalizedRole,
                normalizedKeyword,
                pageable
        );

        List<AdminUserResponse> items = userPage.getContent().stream()
                .map(this::toAdminUserResponse)
                .collect(Collectors.toList());

        return PageResponse.of(items, page, size, userPage.getTotalElements());
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }

        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ADMIN", "MERCHANT", "CUSTOMER").contains(normalized)) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "无效的用户角色: " + role
            );
        }
        return normalized;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 修改用户状态（封禁/解封）
     *
     * 业务流程：
     * 1. 查询用户是否存在
     * 2. 校验状态值（只能为 ACTIVE 或 DISABLED）
     * 3. 更新用户状态
     * 4. 返回更新后的用户信息
     */
    @Transactional
    public AdminUserResponse updateStatus(String userId, UpdateUserStatusRequest request) {
        // 1. 查询用户
        UserEntity user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "用户不存在: " + userId));

        // 2. 校验状态值
        String newStatus = request.status();
        if (!List.of("ACTIVE", "DISABLED").contains(newStatus)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "无效的用户状态: " + newStatus + "，仅支持 ACTIVE 或 DISABLED");
        }

        // 3. 更新状态
        user.setStatus(newStatus);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);

        log.info("管理员修改用户状态: userId={}, newStatus={}", userId, newStatus);

        // 4. 返回更新后的用户信息
        return toAdminUserResponse(user);
    }

    /**
     * 获取平台概览数据
     *
     * 统计信息：
     * - userCount: 总用户数
     * - productCount: 总商品数
     * - orderCount: 总订单数
     * - todayOrderCount: 今日新增订单数
     * - aiServiceStatus: AI 服务状态（当前为模拟）
     * - vectorDbStatus: 向量数据库状态（当前为模拟）
     */
    public AdminMetricsResponse overview() {
        long userCount = userRepository.count();
        long productCount = productRepository.count();
        long orderCount = orderRepository.count();
        boolean aiUp = aiServiceClient.health();

        // 今日新增订单数（从当天 0 点开始统计）
        OffsetDateTime todayStart = OffsetDateTime.now()
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        // 简化实现：使用 count 方法，实际应使用自定义查询
        long todayOrderCount = 0;
        try {
            // 这里使用 findAll 后过滤，生产环境应使用 @Query 统计
            todayOrderCount = orderRepository.findAll().stream()
                    .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(todayStart))
                    .count();
        } catch (Exception e) {
            log.warn("统计今日订单数失败: {}", e.getMessage());
        }

        long searchCountToday = behaviorLogRepository.countByEventTypeAndCreatedAtAfter("SEARCH", todayStart)
                + behaviorLogRepository.countByEventTypeAndCreatedAtAfter("IMAGE_SEARCH", todayStart);
        long chatCountToday = behaviorLogRepository.countByEventTypeAndCreatedAtAfter("CHAT", todayStart);

        log.info("管理员查看平台概览: users={}, products={}, orders={}, todayOrders={}, searches={}, chats={}",
                userCount, productCount, orderCount, todayOrderCount, searchCountToday, chatCountToday);

        return new AdminMetricsResponse(
                userCount,
                productCount,
                orderCount,
                todayOrderCount,
                searchCountToday,
                chatCountToday,
                aiUp ? "UP" : "DOWN",
                aiUp ? "UP" : "UNKNOWN"
        );
    }

    /**
     * 将 UserEntity 转换为 AdminUserResponse
     * 注意：不暴露密码哈希值
     */
    private AdminUserResponse toAdminUserResponse(UserEntity user) {
        return new AdminUserResponse(
                user.getUserId(),
                user.getUsername(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}
