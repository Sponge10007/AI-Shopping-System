package com.aishop.modules.internal;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.response.ApiResponse;
import com.aishop.modules.internal.dto.BatchProductAiSummaryRequest;
import com.aishop.modules.internal.dto.BatchProductAiSummaryResponse;
import com.aishop.modules.internal.dto.ProductAiSummaryResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部商品接口控制器
 *
 * 为 AI Service 提供只读、脱敏、稳定的商品摘要信息。
 *
 * 安全机制：
 * 1. 路径前缀 /internal/v1/ 不暴露到公网（通过网关/Nginx 限制）
 * 2. 需要验证 X-Internal-Token 请求头
 * 3. 只提供只读接口，不提供写入操作
 * 4. 返回数据经过脱敏处理（不暴露商家 ID、数据库 ID 等）
 *
 * 接口列表：
 * - GET  /internal/v1/products/{productId}/ai-summary  — 查询单个商品 AI 摘要
 * - POST /internal/v1/products/ai-summaries             — 批量查询商品 AI 摘要
 */
@RestController
@RequestMapping("/internal/v1/products")
public class InternalProductController {

    private static final Logger log = LoggerFactory.getLogger(InternalProductController.class);

    private final InternalProductService internalProductService;

    /**
     * 内部 Token，从 application.yml 的 app.internal-token 读取
     * 用于验证调用方是否为合法的内部服务
     */
    @Value("${app.internal-token:default-internal-token}")
    private String internalToken;

    public InternalProductController(InternalProductService internalProductService) {
        this.internalProductService = internalProductService;
    }

    /**
     * 查询单个商品的 AI 摘要
     *
     * 请求头要求：
     * - X-Internal-Token: 内部服务认证 Token
     *
     * @param productId 商品业务 ID（如 p10001）
     * @return 商品 AI 摘要
     */
    @GetMapping("/{productId}/ai-summary")
    public ApiResponse<ProductAiSummaryResponse> getAiSummary(
            @PathVariable String productId,
            @RequestHeader("X-Internal-Token") String token) {
        // 验证内部 Token
        validateInternalToken(token);
        return ApiResponse.ok(internalProductService.getAiSummary(productId));
    }

    /**
     * 批量查询商品的 AI 摘要
     *
     * 请求头要求：
     * - X-Internal-Token: 内部服务认证 Token
     *
     * 请求体：
     * - productIds: 商品业务 ID 列表（至少 1 个）
     *
     * 注意：单个商品失败不影响其他商品
     *
     * @param request 批量查询请求
     * @return 批量商品 AI 摘要
     */
    @PostMapping("/ai-summaries")
    public ApiResponse<BatchProductAiSummaryResponse> getAiSummaries(
            @Valid @RequestBody BatchProductAiSummaryRequest request,
            @RequestHeader("X-Internal-Token") String token) {
        // 验证内部 Token
        validateInternalToken(token);
        return ApiResponse.ok(internalProductService.getAiSummaries(request.productIds()));
    }

    /**
     * 验证内部服务 Token
     *
     * 如果 Token 不匹配，抛出 FORBIDDEN 异常
     * 防止未授权的服务调用内部接口
     */
    private void validateInternalToken(String token) {
        if (token == null || !token.equals(internalToken)) {
            log.warn("内部接口认证失败: 无效的 Internal-Token");
            throw new BusinessException(ErrorCode.FORBIDDEN, "无效的内部服务令牌");
        }
    }
}
