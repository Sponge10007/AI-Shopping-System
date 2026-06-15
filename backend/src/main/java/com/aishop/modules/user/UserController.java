package com.aishop.modules.user;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.response.ApiResponse;
import com.aishop.common.security.CurrentUser;
import com.aishop.modules.user.dto.UpdateUserRequest;
import com.aishop.modules.user.dto.UserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户控制器
 *
 * 接口说明：
 * - GET /api/v1/users/me — 获取当前用户信息（需要登录）
 * - PATCH /api/v1/users/me — 更新当前用户信息（需要登录，PATCH 语义）
 *
 * 认证方式：
 * - 需要 Bearer Token，从 SecurityContext 获取当前用户
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> getCurrentUser(
            @AuthenticationPrincipal CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return ApiResponse.ok(userService.getCurrentUser(currentUser));
    }

    @PatchMapping("/me")
    public ApiResponse<UserResponse> updateCurrentUser(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody UpdateUserRequest request) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return ApiResponse.ok(userService.updateCurrentUser(currentUser, request));
    }
}
