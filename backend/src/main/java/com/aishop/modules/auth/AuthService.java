package com.aishop.modules.auth;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.security.jwt.JwtTokenProvider;
import com.aishop.infrastructure.persistence.entity.UserEntity;
import com.aishop.infrastructure.persistence.entity.UserProfileEntity;
import com.aishop.infrastructure.persistence.repository.UserProfileRepository;
import com.aishop.infrastructure.persistence.repository.UserRepository;
import com.aishop.modules.auth.dto.LoginRequest;
import com.aishop.modules.auth.dto.LoginResponse;
import com.aishop.modules.auth.dto.LogoutResponse;
import com.aishop.modules.auth.dto.RegisterRequest;
import com.aishop.modules.auth.dto.RegisterResponse;
import com.aishop.modules.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 认证服务 — 处理注册、登录、登出
 *
 * 技术要点：
 * 1. 密码使用 BCrypt 哈希存储，绝不存明文
 * 2. 注册时同时创建 users 和 user_profiles 两条记录（事务保证）
 * 3. 登录成功返回双 Token（Access + Refresh）
 * 4. userId 由 UserService 统一生成（CUSTOMER → u + 数字，MERCHANT → m + 数字）
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;

    public AuthService(UserRepository userRepository,
                       UserProfileRepository userProfileRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       UserService userService) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
    }

    /**
     * 用户注册
     *
     * 请求体字段说明（RegisterRequest）：
     * - username: 用户名，必填，唯一
     * - phone: 手机号，必填
     * - password: 密码，必填，明文传入，BCrypt 哈希后存储
     * - role: 角色，选填，默认 CUSTOMER，可选值 CUSTOMER | MERCHANT
     *
     * 业务流程：
     * 1. 校验用户名是否已存在 → 重复则抛 DUPLICATE_RESOURCE
     * 2. 校验手机号是否已存在 → 重复则抛 DUPLICATE_RESOURCE
     * 3. 通过 UserService 生成 userId（u10002 / m10002 格式）
     * 4. BCrypt 加密密码
     * 5. 创建 UserEntity 并保存
     * 6. 创建 UserProfileEntity（空画像）并保存
     * 7. 返回 RegisterResponse
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // 1. 校验用户名唯一性
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "用户名已存在: " + request.username());
        }

        // 2. 校验手机号唯一性
        if (userRepository.existsByPhone(request.phone())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "手机号已注册: " + request.phone());
        }

        // 3. 确定角色，并通过 UserService 生成 userId
        String role = (request.role() == null || request.role().isBlank()) ? "CUSTOMER" : request.role();
        String userId = userService.generateNextUserId(role);

        // 4. BCrypt 加密密码
        String passwordHash = passwordEncoder.encode(request.password());

        // 5. 创建用户实体
        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setUsername(request.username());
        user.setPhone(request.phone());
        user.setPasswordHash(passwordHash);
        user.setRole(role);
        user.setStatus("ACTIVE");
        OffsetDateTime now = OffsetDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        // 6. 创建用户画像（初始为空）
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(userId);
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        userProfileRepository.save(profile);

        log.info("用户注册成功: userId={}, username={}, role={}", userId, request.username(), role);

        return new RegisterResponse(userId, request.username(), role);
    }

    /**
     * 用户登录
     *
     * 请求体字段说明（LoginRequest）：
     * - account: 账号，必填，可以是用户名或手机号
     * - password: 密码，必填，明文传入
     *
     * 业务流程：
     * 1. 根据 account（用户名或手机号）查询用户
     * 2. 校验用户是否存在 → 不存在抛 RESOURCE_NOT_FOUND
     * 3. 校验用户状态是否被封禁 → 封禁抛 FORBIDDEN
     * 4. BCrypt 校验密码 → 不匹配抛 UNAUTHORIZED
     * 5. 生成 Access Token（2小时有效）
     * 6. 生成 Refresh Token（7天有效）
     * 7. 返回 LoginResponse
     */
    public LoginResponse login(LoginRequest request) {
        // 1. 查询用户（支持用户名或手机号登录）
        UserEntity user = userRepository.findByUsername(request.account())
                .orElse(null);

        // 如果用户名没查到，尝试用手机号查
        if (user == null) {
            user = userRepository.findByPhone(request.account()).orElse(null);
        }

        // 2. 校验用户是否存在
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "用户不存在: " + request.account());
        }

        // 3. 校验用户状态
        if (user.isBanned()) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "账号已被封禁");
        }

        // 4. 校验密码
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED,
                    "密码错误");
        }

        // 5. 生成 Token
        String accessToken = jwtTokenProvider.generateAccessToken(user.getUserId(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId(), user.getRole());

        log.info("用户登录成功: userId={}, username={}", user.getUserId(), user.getUsername());

        // 6. 返回
        RegisterResponse userInfo = new RegisterResponse(
                user.getUserId(), user.getUsername(), user.getRole());

        return new LoginResponse(accessToken, refreshToken,
                jwtTokenProvider.getAccessTokenExpirationSec(), userInfo);
    }

    /**
     * 用户登出
     *
     * 当前实现：
     * - 返回成功标记
     * - 实际生产环境需要将 Token 加入黑名单（Redis），
     *   但当前阶段简化处理，由前端清除 Token 即可
     *
     * ⚠️ 注意：当前 SecurityConfig 没有 Token 黑名单机制，
     *    登出后 Token 在有效期内仍然可用。
     *    后续优化可引入 Redis 黑名单。
     */
    public LogoutResponse logout() {
        log.info("用户登出");
        return new LogoutResponse(true);
    }
}
