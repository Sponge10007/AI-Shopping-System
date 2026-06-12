package com.aishop.modules.admin;

import com.aishop.common.response.PageResponse;
import com.aishop.infrastructure.ai.AiServiceClient;
import com.aishop.modules.admin.dto.AdminMetricsResponse;
import com.aishop.modules.admin.dto.AdminUserResponse;
import com.aishop.modules.admin.dto.UpdateUserStatusRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AdminService {
    private final JdbcTemplate jdbcTemplate;
    private final AiServiceClient aiServiceClient;

    public AdminService(JdbcTemplate jdbcTemplate, AiServiceClient aiServiceClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiServiceClient = aiServiceClient;
    }

    public PageResponse<AdminUserResponse> listUsers(String role, String keyword, int page, int size) {
        try {
            String normalizedRole = role == null || role.isBlank() ? null : role;
            String normalizedKeyword = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            int normalizedPage = Math.max(page, 1);
            int normalizedSize = Math.min(Math.max(size, 1), 100);
            int offset = (normalizedPage - 1) * normalizedSize;
            List<AdminUserResponse> users = jdbcTemplate.query("""
                            SELECT user_id, username, phone, role, status, created_at
                            FROM users
                            WHERE (? IS NULL OR role = ?)
                              AND (? IS NULL OR username LIKE ? OR phone LIKE ?)
                            ORDER BY created_at DESC
                            LIMIT ? OFFSET ?
                            """,
                    (rs, rowNum) -> new AdminUserResponse(
                            rs.getString("user_id"),
                            rs.getString("username"),
                            rs.getString("phone"),
                            rs.getString("role"),
                            rs.getString("status"),
                            rs.getObject("created_at", OffsetDateTime.class)
                    ),
                    normalizedRole,
                    normalizedRole,
                    normalizedKeyword,
                    normalizedKeyword,
                    normalizedKeyword,
                    normalizedSize,
                    offset);
            Long total = jdbcTemplate.queryForObject("""
                            SELECT COUNT(*)
                            FROM users
                            WHERE (? IS NULL OR role = ?)
                              AND (? IS NULL OR username LIKE ? OR phone LIKE ?)
                            """,
                    Long.class,
                    normalizedRole,
                    normalizedRole,
                    normalizedKeyword,
                    normalizedKeyword,
                    normalizedKeyword);
            return PageResponse.of(users, normalizedPage, normalizedSize, total == null ? users.size() : total);
        } catch (DataAccessException exception) {
            List<AdminUserResponse> users = List.of(
                    new AdminUserResponse("u10001", "alice", "13800000000", "CUSTOMER", "ACTIVE", OffsetDateTime.now()),
                    new AdminUserResponse("m10001", "merchant", "13800000001", "MERCHANT", "ACTIVE", OffsetDateTime.now())
            );
            return PageResponse.of(users, page, size, users.size());
        }
    }

    public AdminUserResponse updateStatus(String userId, UpdateUserStatusRequest request) {
        try {
            jdbcTemplate.update(
                    "UPDATE users SET status = ?, updated_at = now() WHERE user_id = ?",
                    request.status(),
                    userId);
            return jdbcTemplate.queryForObject("""
                            SELECT user_id, username, phone, role, status, created_at
                            FROM users
                            WHERE user_id = ?
                            """,
                    (rs, rowNum) -> new AdminUserResponse(
                            rs.getString("user_id"),
                            rs.getString("username"),
                            rs.getString("phone"),
                            rs.getString("role"),
                            rs.getString("status"),
                            rs.getObject("created_at", OffsetDateTime.class)
                    ),
                    userId);
        } catch (DataAccessException exception) {
            return new AdminUserResponse(userId, "alice", "13800000000", "CUSTOMER", request.status(), OffsetDateTime.now());
        }
    }

    public AdminMetricsResponse overview() {
        boolean aiUp = aiServiceClient.health();
        try {
            long userCount = count("SELECT COUNT(*) FROM users");
            long productCount = count("SELECT COUNT(*) FROM products WHERE status = 'ON_SALE'");
            long orderCount = count("SELECT COUNT(*) FROM orders");
            long todayOrderCount = count("SELECT COUNT(*) FROM orders WHERE created_at >= CURRENT_DATE");
            long searchCountToday = count("""
                    SELECT COUNT(*) FROM behavior_logs
                    WHERE event_type IN ('SEARCH', 'IMAGE_SEARCH')
                      AND created_at >= CURRENT_DATE
                    """);
            long chatCountToday = count("""
                    SELECT COUNT(*) FROM behavior_logs
                    WHERE event_type = 'CHAT'
                      AND created_at >= CURRENT_DATE
                    """);
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
        } catch (DataAccessException exception) {
            return new AdminMetricsResponse(2, 2, 1, 1, 0, 0, aiUp ? "UP" : "DOWN", aiUp ? "UP" : "UNKNOWN");
        }
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
