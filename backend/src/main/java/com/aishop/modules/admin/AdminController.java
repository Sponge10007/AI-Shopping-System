package com.aishop.modules.admin;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.response.ApiResponse;
import com.aishop.common.response.PageResponse;
import com.aishop.common.security.CurrentUser;
import com.aishop.modules.admin.dto.AdminMetricsResponse;
import com.aishop.modules.admin.dto.AdminUserResponse;
import com.aishop.modules.admin.dto.UpdateUserStatusRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员控制器
 *
 * 接口说明：
 * - GET /api/v1/admin/users — 用户管理列表（需要 ADMIN 角色）
 * - PATCH /api/v1/admin/users/{userId}/status — 修改用户状态（需要 ADMIN 角色）
 * - GET /api/v1/admin/metrics/overview — 平台概览数据（需要 ADMIN 角色）
 *
 * 权限校验：
 * - 所有接口需要 ADMIN 角色
 * - 非 ADMIN 角色访问返回 403
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserResponse>> listUsers(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        checkAdminRole(currentUser);
        return ApiResponse.ok(adminService.listUsers(role, keyword, page, size));
    }

    @PatchMapping("/users/{userId}/status")
    public ApiResponse<AdminUserResponse> updateStatus(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        checkAdminRole(currentUser);
        return ApiResponse.ok(adminService.updateStatus(userId, request));
    }

    @GetMapping("/metrics/overview")
    public ApiResponse<AdminMetricsResponse> overview(
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        checkAdminRole(currentUser);
        return ApiResponse.ok(adminService.overview());
    }

    /**
     * 校验当前用户是否为 ADMIN 角色
     */
    private void checkAdminRole(CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        if (!"ADMIN".equals(currentUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可以执行此操作");
        }
    }
}
