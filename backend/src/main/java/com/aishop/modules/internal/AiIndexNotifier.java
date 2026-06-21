package com.aishop.modules.internal;

import com.aishop.infrastructure.ai.AiServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

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

    private final AiServiceClient aiServiceClient;

    /**
     * 是否启用 AI 索引通知（默认启用）
     * 在开发或测试环境中可以关闭，避免依赖 AI Service
     */
    @Value("${app.ai-service.index-notify-enabled:true}")
    private boolean indexNotifyEnabled = true;

    public AiIndexNotifier(AiServiceClient aiServiceClient) {
        this.aiServiceClient = aiServiceClient;
    }

    /**
     * 异步通知 AI Service：商品已创建，需要添加向量索引
     *
     * @param productId 商品业务 ID
     * @param description 用于生成向量的商品描述
     */
    @Async
    public void notifyProductCreated(String productId, String description) {
        if (!indexNotifyEnabled) {
            log.debug("AI索引通知已禁用，跳过创建通知: productId={}", productId);
            return;
        }
        if (aiServiceClient.indexProduct(productId, description)) {
            log.info("AI索引创建通知已发送: productId={}", productId);
        } else {
            log.warn("AI索引创建通知发送失败: productId={}", productId);
        }
    }

    /**
     * 异步通知 AI Service：商品已更新，需要更新向量索引
     *
     * @param productId 商品业务 ID
     * @param description 用于生成向量的商品描述
     */
    @Async
    public void notifyProductUpdated(String productId, String description) {
        if (!indexNotifyEnabled) {
            log.debug("AI索引通知已禁用，跳过更新通知: productId={}", productId);
            return;
        }
        if (aiServiceClient.indexProduct(productId, description)) {
            log.info("AI索引更新通知已发送: productId={}", productId);
        } else {
            log.warn("AI索引更新通知发送失败: productId={}", productId);
        }
    }

    /**
     * 异步通知 AI Service：商品已下架，需要删除向量索引
     *
     * @param productId 商品业务 ID
     */
    @Async
    public void notifyProductDeleted(String productId) {
        if (!indexNotifyEnabled) {
            log.debug("AI索引通知已禁用，跳过删除通知: productId={}", productId);
            return;
        }
        if (aiServiceClient.deleteProductIndex(productId)) {
            log.info("AI索引删除通知已发送: productId={}", productId);
        } else {
            log.warn("AI索引删除通知发送失败: productId={}", productId);
        }
    }
}
