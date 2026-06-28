package com.aishop.modules.internal;

import com.aishop.infrastructure.ai.AiServiceClient;
import com.aishop.modules.product.ProductService;
import com.aishop.modules.product.dto.ProductResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 启动后自动补齐在售商品的向量索引。
 *
 * 数据库卷和 Chroma 卷可能分别保留或重建，不能假设两边商品数量天然一致。
 * 本服务在 AI 服务可用后执行一次幂等 upsert，保证自然语言搜索不会漏掉存量商品。
 */
@Service
@EnableScheduling
public class AiIndexBackfillService {

    private static final Logger log = LoggerFactory.getLogger(AiIndexBackfillService.class);

    private final ProductService productService;
    private final AiServiceClient aiServiceClient;
    private final AtomicBoolean completed = new AtomicBoolean(false);

    @Value("${app.ai-service.index-backfill-enabled:true}")
    private boolean enabled;

    public AiIndexBackfillService(ProductService productService, AiServiceClient aiServiceClient) {
        this.productService = productService;
        this.aiServiceClient = aiServiceClient;
    }

    @Scheduled(initialDelay = 3000, fixedDelay = 15000)
    public void backfillWhenReady() {
        if (!enabled || completed.get() || !aiServiceClient.health()) {
            return;
        }

        int success = 0;
        var products = productService.searchableProducts(500);
        for (ProductResponse product : products) {
            if (aiServiceClient.indexProduct(product.productId(), buildIndexDescription(product))) {
                success++;
            }
        }

        if (success == products.size()) {
            completed.set(true);
            log.info("AI商品索引补齐任务已提交: count={}", success);
        } else {
            log.warn("AI商品索引补齐未完全成功，将自动重试: success={}, total={}", success, products.size());
        }
    }

    private String buildIndexDescription(ProductResponse product) {
        StringBuilder description = new StringBuilder();
        append(description, product.name());
        append(description, product.categoryName());
        append(description, product.description());
        if (product.tags() != null && !product.tags().isEmpty()) {
            append(description, String.join("，", product.tags()));
        }
        append(description, "价格" + product.price() + "元");
        return description.toString();
    }

    private void append(StringBuilder target, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!target.isEmpty()) {
            target.append("，");
        }
        target.append(value.trim());
    }
}
