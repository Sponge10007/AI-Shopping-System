package com.aishop.modules.behavior;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.response.ApiResponse;
import com.aishop.common.security.CurrentUser;
import com.aishop.modules.behavior.dto.BehaviorEventRequest;
import com.aishop.modules.behavior.dto.BehaviorEventResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户行为控制器
 *
 * 接口说明：
 * - POST /api/v1/behavior-events — 上报用户行为事件
 * - 需要登录（由 JWT 过滤器校验）
 * - 行为数据用于推荐系统分析
 *
 * 异常处理：
 * - @Valid 校验失败 → MethodArgumentNotValidException → GlobalExceptionHandler → 400 JSON
 * - 无 Token 访问 → JWT 过滤器 → 401 JSON
 * - 业务异常 → BusinessException → GlobalExceptionHandler → 对应状态码 JSON
 */
@RestController
@RequestMapping("/api/v1/behavior-events")
public class BehaviorController {
    private final BehaviorService behaviorService;

    public BehaviorController(BehaviorService behaviorService) {
        this.behaviorService = behaviorService;
    }

    @PostMapping
    public ApiResponse<BehaviorEventResponse> record(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody BehaviorEventRequest request) {
        // 校验登录
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        BehaviorEventResponse response = behaviorService.recordEvent(currentUser.userId(), request);
        return ApiResponse.ok(response);
    }
}
