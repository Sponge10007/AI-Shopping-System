package com.aishop.modules.user;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.security.CurrentUser;
import com.aishop.infrastructure.persistence.entity.UserEntity;
import com.aishop.infrastructure.persistence.entity.UserProfileEntity;
import com.aishop.infrastructure.persistence.repository.UserProfileRepository;
import com.aishop.infrastructure.persistence.repository.UserRepository;
import com.aishop.modules.user.dto.UpdateUserRequest;
import com.aishop.modules.user.dto.UserResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户服务 — 管理用户信息与 ID 生成
 *
 * 职责：
 * 1. 用户信息查询与更新（GET/PATCH /api/v1/users/me）
 * 2. 用户 ID 生成（CUSTOMER → u + 数字，MERCHANT → m + 数字）
 *    启动时从数据库读取最大 userId 初始化计数器，防止与已有数据冲突
 *
 * 技术要点：
 * - 查询用户信息时同时查询 users 表和 user_profiles 表
 * - PATCH 更新时只更新提供的字段，未提供的字段保持不变
 * - 手机号不允许通过此接口修改（需要走单独的验证流程）
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    /**
     * 自增 ID 生成器（生产环境应使用分布式 ID 方案如 Snowflake）
     * 启动时从数据库最大 ID 初始化，防止与已有数据冲突
     */
    private final AtomicLong userIdCounter = new AtomicLong(10001);

    public UserService(UserRepository userRepository,
                       UserProfileRepository userProfileRepository) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
    }

    /**
     * 启动时从数据库读取最大 userId 数字部分，初始化计数器
     */
    @PostConstruct
    public void initUserIdCounter() {
        try {
            Long maxId = userRepository.findMaxUserIdNumeric();
            if (maxId != null && maxId > 0) {
                userIdCounter.set(maxId + 1);
                log.info("用户ID计数器已从数据库初始化: maxId={}, nextId=m{}/u{}", maxId, maxId + 1, maxId + 1);
            } else {
                log.info("用户ID计数器使用默认初始值: 10001");
            }
        } catch (Exception e) {
            log.warn("初始化用户ID计数器失败，使用默认值: {}", e.getMessage());
        }
    }

    /**
     * 生成下一个用户 ID
     *
     * @param role 用户角色（CUSTOMER / MERCHANT）
     * @return 格式化的用户 ID，如 u10002 / m10002
     */
    public String generateNextUserId(String role) {
        String prefix = "MERCHANT".equals(role) ? "m" : "u";
        synchronized (userIdCounter) {
            return prefix + userIdCounter.getAndIncrement();
        }
    }

    // ===== 用户信息查询与更新 =====

    /**
     * 获取当前登录用户的个人信息
     *
     * 从 users 表获取基本信息，从 user_profiles 表获取扩展信息
     */
    public UserResponse getCurrentUser(CurrentUser currentUser) {
        // 查询用户
        UserEntity user = userRepository.findByUserId(currentUser.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "用户不存在: " + currentUser.userId()));

        // 查询用户画像
        UserProfileEntity profile = userProfileRepository.findByUserId(currentUser.userId())
                .orElse(null);

        return toUserResponse(user, profile);
    }

    /**
     * 更新当前登录用户的个人信息（PATCH 语义）
     *
     * 只更新提供的字段，未提供的字段保持不变。
     * 手机号不允许通过此接口修改。
     */
    @Transactional
    public UserResponse updateCurrentUser(CurrentUser currentUser, UpdateUserRequest request) {
        // 查询用户
        UserEntity user = userRepository.findByUserId(currentUser.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "用户不存在: " + currentUser.userId()));

        // 查询用户画像
        UserProfileEntity profile = userProfileRepository.findByUserId(currentUser.userId())
                .orElseGet(() -> {
                    // 如果画像不存在，创建一个新的
                    UserProfileEntity newProfile = new UserProfileEntity();
                    newProfile.setUserId(currentUser.userId());
                    newProfile.setCreatedAt(OffsetDateTime.now());
                    newProfile.setUpdatedAt(OffsetDateTime.now());
                    return newProfile;
                });

        OffsetDateTime now = OffsetDateTime.now();

        // PATCH 语义：只更新非 null 的字段
        boolean updated = false;

        if (request.nickname() != null) {
            profile.setNickname(request.nickname());
            updated = true;
        }

        if (request.avatarUrl() != null) {
            profile.setAvatarUrl(request.avatarUrl());
            updated = true;
        }

        if (updated) {
            profile.setUpdatedAt(now);
            userProfileRepository.save(profile);
            log.info("用户信息更新成功: userId={}", currentUser.userId());
        }

        return toUserResponse(user, profile);
    }

    /**
     * 将 UserEntity + UserProfileEntity 转换为 UserResponse
     */
    private UserResponse toUserResponse(UserEntity user, UserProfileEntity profile) {
        String nickname = (profile != null && profile.getNickname() != null)
                ? profile.getNickname() : user.getUsername();
        String avatarUrl = (profile != null) ? profile.getAvatarUrl() : null;

        return new UserResponse(
                user.getUserId(),
                user.getUsername(),
                user.getPhone(),
                user.getRole(),
                nickname,
                avatarUrl,
                user.getCreatedAt()
        );
    }
}
