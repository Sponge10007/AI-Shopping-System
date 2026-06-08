package com.aishop.modules.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * AI 索引更新通知服务
 *
 * 当商品发生变更（创建、更新、下架）时，异步通知 AI Service 更新向量索引。
 *
 * 通知场景：
 * 1. 商品创建（ON_SALE）→ 通知 AI Service 添加向量索引
 * 2. 商品更新（name/description/tags 改变）→ 通知 AI Service 更新向量索引
 * 3. 商品下架（OFF_SALE）→ 通知 AI Service 删除向量索引
 *
 * 设计说明：
 * - 使用 @Async 异步执行，不阻塞主业务流程
 * - 通知失败不影响主业务流程（只记录日志）
 * - 支持通过配置开关启用/禁用通知
 * - 支持配置 AI Service 的 base-url
 */
@Service
@EnableAsync
public class AiIndexNotifier {

    private static final Logger log = LoggerFactory.getLogger(AiIndexNotifier.class);

    private final RestTemplate restTemplate;

    /**
     * AI Service 的基础 URL
     */
    @Value("${app.ai-service.base-url:http://localhost:8001/internal/v1/ai}")
    private String aiServiceBaseUrl;

    /**
     * 内部 Token，用于调用 AI Service 时的认证
     */
    @Value("${app.internal-token:default-internal-token}")
    private String internalToken;

    /**
     * 是否启用 AI 索引通知（默认启用）
     * 在开发或测试环境中可以关闭，避免依赖 AI Service
     */
    @Value("${app.ai-service.index-notify-enabled:true}")
    private boolean indexNotifyEnabled;

    public AiIndexNotifier() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 异步通知 AI Service：商品已创建，需要添加向量索引
     *
     * @param productId 商品业务 ID
     * @param productName 商品名称
     */
    @Async
    public void notifyProductCreated(String productId, String productName) {
        if (!indexNotifyEnabled) {
            log.debug("AI索引通知已禁用，跳过创建通知: productId={}", productId);
            return;
        }
        try {
            String url = aiServiceBaseUrl + "/index/product-created";
            Map<String, Object> body = Map.of(
                    "productId", productId,
                    "productName", productName,
                    "action", "CREATE"
            );
            restTemplate.postForEntity(url, body, String.class);
            log.info("AI索引创建通知已发送: productId={}", productId);
        } catch (Exception e) {
            // 通知失败不影响主业务流程
            log.warn("AI索引创建通知发送失败: productId={}, error={}", productId, e.getMessage());
        }
    }

    /**
     * 异步通知 AI Service：商品已更新，需要更新向量索引
     *
     * @param productId 商品业务 ID
     * @param productName 商品名称
     */
    @Async
    public void notifyProductUpdated(String productId, String productName) {
        if (!indexNotifyEnabled) {
            log.debug("AI索引通知已禁用，跳过更新通知: productId={}", productId);
            return;
        }
        try {
            String url = aiServiceBaseUrl + "/index/product-updated";
            Map<String, Object> body = Map.of(
                    "productId", productId,
                    "productName", productName,
                    "action", "UPDATE"
            );
            restTemplate.postForEntity(url, body, String.class);
            log.info("AI索引更新通知已发送: productId={}", productId);
        } catch (Exception e) {
            log.warn("AI索引更新通知发送失败: productId={}, error={}", productId, e.getMessage());
        }
    }

    /**
     * 异步通知 AI Service：商品已下架，需要删除向量索引
     *
     * @param productId 商品业务 ID
     * @param productName 商品名称
     */
    @Async
    public void notifyProductDeleted(String productId, String productName) {
        if (!indexNotifyEnabled) {
            log.debug("AI索引通知已禁用，跳过删除通知: productId={}", productId);
            return;
        }
        try {
            String url = aiServiceBaseUrl + "/index/product-deleted";
            Map<String, Object> body = Map.of(
                    "productId", productId,
                    "productName", productName,
                    "action", "DELETE"
            );
            restTemplate.postForEntity(url, body, String.class);
            log.info("AI索引删除通知已发送: productId={}", productId);
        } catch (Exception e) {
            log.warn("AI索引删除通知发送失败: productId={}, error={}", productId, e.getMessage());
        }
    }
}
