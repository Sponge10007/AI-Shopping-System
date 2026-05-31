package com.aishop.modules.admin;

import com.aishop.common.response.ApiResponse;
import com.aishop.common.response.PageResponse;
import com.aishop.modules.admin.dto.AdminMetricsResponse;
import com.aishop.modules.admin.dto.AdminUserResponse;
import com.aishop.modules.admin.dto.UpdateUserStatusRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserResponse>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(adminService.listUsers(role, keyword, page, size));
    }

    @PatchMapping("/users/{userId}/status")
    public ApiResponse<AdminUserResponse> updateStatus(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        return ApiResponse.ok(adminService.updateStatus(userId, request));
    }

    @GetMapping("/metrics/overview")
    public ApiResponse<AdminMetricsResponse> overview() {
        return ApiResponse.ok(adminService.overview());
    }
}

