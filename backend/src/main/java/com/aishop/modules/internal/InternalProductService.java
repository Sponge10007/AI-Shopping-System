package com.aishop.modules.internal;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.modules.internal.dto.BatchProductAiSummaryResponse;
import com.aishop.modules.internal.dto.ProductAiSummaryResponse;
import com.aishop.modules.product.ProductService;
import com.aishop.modules.product.dto.ProductResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 内部商品摘要服务 — 为 AI Service 提供商品摘要信息
 *
 * 职责：
 * 1. 提供单个商品的 AI 摘要（结构化文本，包含名称、价格、描述、分类、标签等）
 * 2. 提供批量商品的 AI 摘要（单个失败不影响整体）
 * 3. 对敏感信息进行脱敏处理（如手机号、地址等不暴露）
 * 4. 生成标准化的摘要格式，便于 AI 模型理解
 *
 * 使用场景：
 * - AI 聊天机器人需要了解商品信息时调用
 * - 推荐系统需要商品特征时调用
 * - 语义搜索需要商品描述时调用
 *
 * 安全说明：
 * - 本接口仅限内部服务调用（通过 /internal/v1/ 路径 + Internal-Token 验证）
 * - 不暴露商家敏感信息（如 merchantId）
 * - 不暴露数据库自增 ID
 */
@Service
public class InternalProductService {

    private static final Logger log = LoggerFactory.getLogger(InternalProductService.class);

    private final ProductService productService;

    public InternalProductService(ProductService productService) {
        this.productService = productService;
    }

    /**
     * 获取单个商品的 AI 摘要
     *
     * 生成结构化摘要文本，包含：
     * - 商品名称、价格、库存
     * - 分类信息
     * - 商品描述（截取前 200 字）
     * - 标签列表
     * - 评分和销量
     * - 详情页链接
     *
     * @param productId 商品业务 ID
     * @return 商品 AI 摘要响应
     * @throws BusinessException 如果商品不存在
     */
    public ProductAiSummaryResponse getAiSummary(String productId) {
        log.debug("获取商品AI摘要: productId={}", productId);

        // 查询商品详情（如果商品不存在，ProductService 会抛出 RESOURCE_NOT_FOUND）
        ProductResponse product = productService.getProduct(productId);

        // 生成结构化摘要
        String summary = buildStructuredSummary(product);

        log.info("商品AI摘要生成成功: productId={}, summaryLength={}", productId, summary.length());

        return new ProductAiSummaryResponse(productId, summary);
    }

    /**
     * 批量获取商品的 AI 摘要
     *
     * 与单个查询的区别：
     * 1. 单个商品失败不影响其他商品（错误隔离）
     * 2. 每个商品独立生成摘要
     * 3. 返回结果中包含所有成功生成的摘要
     *
     * @param productIds 商品业务 ID 列表
     * @return 批量商品 AI 摘要响应
     */
    public BatchProductAiSummaryResponse getAiSummaries(List<String> productIds) {
        log.debug("批量获取商品AI摘要: count={}", productIds.size());

        List<ProductAiSummaryResponse> items = new ArrayList<>();

        for (String productId : productIds) {
            try {
                ProductAiSummaryResponse summary = getAiSummary(productId);
                items.add(summary);
            } catch (BusinessException e) {
                // 单个商品失败不影响其他商品
                log.warn("批量摘要中跳过失败商品: productId={}, reason={}", productId, e.getMessage());
                // 仍然添加一个失败的摘要记录，方便调用方知晓
                items.add(new ProductAiSummaryResponse(productId,
                        "【摘要生成失败】商品不存在或已下架: " + productId));
            } catch (Exception e) {
                log.error("批量摘要中发生未知错误: productId={}", productId, e);
                items.add(new ProductAiSummaryResponse(productId,
                        "【摘要生成失败】系统内部错误: " + productId));
            }
        }

        log.info("批量商品AI摘要生成完成: total={}, success={}, failed={}",
                productIds.size(), items.stream().filter(i -> !i.summaryText().startsWith("【摘要生成失败】")).count(),
                items.stream().filter(i -> i.summaryText().startsWith("【摘要生成失败】")).count());

        return new BatchProductAiSummaryResponse(items);
    }

    // ==================== 私有方法 ====================

    /**
     * 构建结构化摘要文本
     *
     * 摘要格式设计原则：
     * 1. 结构化：使用"字段名：值"的格式，便于 AI 解析
     * 2. 完整性：包含所有对 AI 有用的商品信息
     * 3. 简洁性：描述字段截取前 200 字，避免过长
     * 4. 安全性：不暴露商家 ID、数据库 ID 等敏感信息
     */
    private String buildStructuredSummary(ProductResponse product) {
        StringBuilder sb = new StringBuilder();

        // 基本信息
        sb.append("商品名称：").append(product.name()).append("\n");
        sb.append("价格：").append(product.price()).append(" 元\n");
        sb.append("库存：").append(product.stock()).append(" 件\n");

        // 分类信息
        if (product.categoryName() != null && !product.categoryName().isBlank()) {
            sb.append("分类：").append(product.categoryName()).append("\n");
        }

        // 商品描述（截取前 200 字，避免摘要过长）
        String description = product.description();
        if (description != null && !description.isBlank()) {
            if (description.length() > 200) {
                description = description.substring(0, 200) + "...";
            }
            sb.append("描述：").append(description).append("\n");
        }

        // 标签
        List<String> tags = product.tags();
        if (tags != null && !tags.isEmpty()) {
            sb.append("特点：").append(String.join("、", tags)).append("\n");
        }

        // 评分和销量
        sb.append("评分：").append(product.rating()).append(" 分\n");
        sb.append("销量：").append(product.sales()).append(" 件\n");

        // 状态
        sb.append("状态：").append("ON_SALE".equals(product.status()) ? "在售" : "已下架").append("\n");

        // 详情页链接
        sb.append("详情页：").append(product.detailUrl());

        return sb.toString();
    }
}
