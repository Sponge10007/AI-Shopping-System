package com.aishop.modules.behavior;

import com.aishop.common.security.CurrentUser;
import com.aishop.modules.behavior.dto.BehaviorEventRequest;
import com.aishop.modules.behavior.dto.BehaviorEventResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BehaviorService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public BehaviorService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public BehaviorEventResponse record(BehaviorEventRequest request) {
        recordForUser(CurrentUser.prototypeCustomer().userId(), request);
        return new BehaviorEventResponse(true);
    }

    public void recordForUser(String userId, BehaviorEventRequest request) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO behavior_logs (user_id, event_type, product_id, query, metadata)
                            VALUES (?, ?, ?, ?, ?::jsonb)
                            """,
                    userId,
                    request.eventType(),
                    request.productId(),
                    request.query(),
                    toJson(request.metadata()));
        } catch (DataAccessException exception) {
            // 行为日志不应阻断主流程，数据库未启动时允许接口继续降级联调。
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }
}
