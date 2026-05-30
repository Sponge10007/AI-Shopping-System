package com.aishop.modules.admin;

import com.aishop.common.response.PageResponse;
import com.aishop.modules.admin.dto.AdminMetricsResponse;
import com.aishop.modules.admin.dto.AdminUserResponse;
import com.aishop.modules.admin.dto.UpdateUserStatusRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AdminService {

    public PageResponse<AdminUserResponse> listUsers(String role, String keyword, int page, int size) {
        List<AdminUserResponse> users = List.of(
                new AdminUserResponse("u10001", "alice", "13800000000", "CUSTOMER", "ACTIVE", OffsetDateTime.now()),
                new AdminUserResponse("m10001", "merchant", "13800000001", "MERCHANT", "ACTIVE", OffsetDateTime.now())
        );
        return PageResponse.of(users, page, size, users.size());
    }

    public AdminUserResponse updateStatus(String userId, UpdateUserStatusRequest request) {
        return new AdminUserResponse(userId, "alice", "13800000000", "CUSTOMER", request.status(), OffsetDateTime.now());
    }

    public AdminMetricsResponse overview() {
        return new AdminMetricsResponse(2, 2, 1, 1, "UP", "UP");
    }
}

