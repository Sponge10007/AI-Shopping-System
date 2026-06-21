package com.aishop.modules.product;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.response.PageResponse;
import com.aishop.common.security.CurrentUser;
import com.aishop.infrastructure.persistence.entity.ProductEntity;
import com.aishop.infrastructure.persistence.entity.ProductImageEntity;
import com.aishop.infrastructure.persistence.repository.ProductImageRepository;
import com.aishop.infrastructure.persistence.repository.ProductRepository;
import com.aishop.modules.internal.AiIndexNotifier;
import com.aishop.modules.product.dto.CreateProductRequest;
import com.aishop.modules.product.dto.ProductMutationResponse;
import com.aishop.modules.product.dto.ProductResponse;
import com.aishop.modules.product.dto.ProductSummaryResponse;
import com.aishop.modules.product.dto.RestockRequest;
import com.aishop.modules.product.dto.RestockResponse;
import com.aishop.modules.product.dto.UpdateProductRequest;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 商品服务 — 处理商品的创建、查询、更新、下架、补货
 *
 * 技术要点：
 * 1. productId 生成规则：p + 数字（如 p10001），启动时从数据库最大ID初始化
 * 2. 图片存储：使用 product_images 表独立存储，支持多图排序
 * 3. 标签存储：以逗号分隔的字符串存入 tags 字段
 * 4. 下架为逻辑删除（修改 status = 'OFF_SALE'）
 * 5. 补货使用原子操作防止并发问题
 * 6. AI 索引通知：商品创建/更新/下架后异步通知 AI Service 更新向量索引
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final AiIndexNotifier aiIndexNotifier;

    /**
     * 自增 ID 生成器，启动时从数据库最大 ID 初始化
     */
    private final AtomicLong productIdCounter = new AtomicLong(10001);

    /**
     * 分类名称映射（简化实现，生产环境应从数据库分类表读取）
     */
    private static final java.util.Map<String, String> CATEGORY_NAMES = java.util.Map.of(
            "c_headphone", "耳机",
            "c_phone", "手机",
            "c_computer", "电脑",
            "c_accessory", "配件",
            "c_home", "家居",
            "c_food", "食品",
            "c_clothing", "服装",
            "c_books", "图书"
    );

    public ProductService(ProductRepository productRepository,
                          ProductImageRepository productImageRepository,
                          AiIndexNotifier aiIndexNotifier) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.aiIndexNotifier = aiIndexNotifier;
    }

    /**
     * 启动时从数据库读取最大 productId 数字部分，初始化计数器
     */
    @PostConstruct
    public void initProductIdCounter() {
        try {
            Long maxId = productRepository.findMaxProductIdNumeric();
            if (maxId != null && maxId > 0) {
                productIdCounter.set(maxId + 1);
                log.info("商品ID计数器已从数据库初始化: maxId={}, nextId=p{}", maxId, maxId + 1);
            } else {
                log.info("商品ID计数器使用默认初始值: p10001");
            }
        } catch (Exception e) {
            log.warn("初始化商品ID计数器失败，使用默认值: {}", e.getMessage());
        }
    }

    // ==================== 公开查询接口 ====================

    /**
     * 查询在售商品列表（公开接口，无需登录）
     *
     * 支持分页、分类筛选、排序（按价格/创建时间）
     */
    public PageResponse<ProductSummaryResponse> listProducts(int page, int size,
                                                              String categoryId,
                                                              String sortBy,
                                                              String sortOrder) {
        // 参数校验
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;

        // 构建排序
        Sort sort = buildSort(sortBy, sortOrder);
        Pageable pageable = PageRequest.of(page - 1, size, sort);

        // 查询
        Page<ProductEntity> productPage;
        if (categoryId != null && !categoryId.isBlank()) {
            productPage = productRepository.findByStatusAndCategoryId("ON_SALE", categoryId, pageable);
        } else {
            productPage = productRepository.findByStatus("ON_SALE", pageable);
        }

        // 转换为 DTO
        List<ProductSummaryResponse> items = productPage.getContent().stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());

        return PageResponse.of(items, page, size, productPage.getTotalElements());
    }

    /**
     * 获取商品详情（公开接口，无需登录）
     */
    public ProductResponse getProduct(String productId) {
        ProductEntity product = findProductOrThrow(productId);

        // 查询图片
        List<ProductImageEntity> images = productImageRepository
                .findByProductIdOrderBySortOrderAsc(productId);

        return toProductResponse(product, images);
    }

    // ==================== 商家接口 ====================

    /**
     * 商家查询自己的商品列表
     */
    public PageResponse<ProductResponse> listMerchantProducts(CurrentUser currentUser,
                                                               String status,
                                                               int page, int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ProductEntity> productPage;
        if (status != null && !status.isBlank()) {
            productPage = productRepository.findByMerchantIdAndStatus(
                    currentUser.userId(), status, pageable);
        } else {
            productPage = productRepository.findByMerchantId(currentUser.userId(), pageable);
        }

        List<ProductResponse> items = productPage.getContent().stream()
                .map(p -> {
                    List<ProductImageEntity> images = productImageRepository
                            .findByProductIdOrderBySortOrderAsc(p.getProductId());
                    return toProductResponse(p, images);
                })
                .collect(Collectors.toList());

        return PageResponse.of(items, page, size, productPage.getTotalElements());
    }

    /**
     * 商家创建商品
     *
     * 创建成功后异步通知 AI Service 更新向量索引
     */
    @Transactional
    public ProductMutationResponse createProduct(CurrentUser currentUser, CreateProductRequest request) {
        // 参数校验
        if (request.name() == null || request.name().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "商品名称不能为空");
        }
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "商品价格必须大于0");
        }
        if (request.stock() < 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "库存不能为负数");
        }

        // 生成 productId（同步保证唯一性）
        String productId;
        synchronized (productIdCounter) {
            productId = "p" + productIdCounter.getAndIncrement();
        }

        // 解析分类名称
        String categoryName = resolveCategoryName(request.categoryId());

        // 创建商品实体
        ProductEntity product = new ProductEntity();
        product.setProductId(productId);
        product.setMerchantId(currentUser.userId());
        product.setName(request.name());
        product.setDescription(request.description() != null ? request.description() : "");
        product.setCategoryId(request.categoryId());
        product.setCategoryName(categoryName);
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setSales(0);
        product.setRating(BigDecimal.ZERO);
        product.setStatus("ON_SALE");
        product.setTags(request.tags() != null ? String.join(",", request.tags()) : null);

        OffsetDateTime now = OffsetDateTime.now();
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        productRepository.save(product);

        // 保存图片
        saveProductImages(productId, request.imageUrls());

        log.info("商品创建成功: productId={}, name={}, merchantId={}",
                productId, request.name(), currentUser.userId());

        // 异步通知 AI Service 更新向量索引
        aiIndexNotifier.notifyProductCreated(productId, buildIndexDescription(product));

        return new ProductMutationResponse(productId, "ON_SALE", "PENDING");
    }

    /**
     * 商家更新商品（PATCH 语义，只更新提供的字段）
     *
     * 更新成功后异步通知 AI Service 更新向量索引
     */
    @Transactional
    public ProductResponse updateProduct(CurrentUser currentUser, String productId, UpdateProductRequest request) {
        ProductEntity product = findProductOrThrow(productId);

        // 校验权限
        if (!product.getMerchantId().equals(currentUser.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权修改此商品");
        }

        // 记录更新前的名称，用于判断是否需要通知 AI
        String oldName = product.getName();

        // PATCH 语义：只更新非 null 的字段
        if (request.name() != null) {
            product.setName(request.name());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.price() != null) {
            if (request.price().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "商品价格必须大于0");
            }
            product.setPrice(request.price());
        }
        if (request.tags() != null) {
            product.setTags(String.join(",", request.tags()));
        }

        product.setUpdatedAt(OffsetDateTime.now());
        productRepository.save(product);

        // 更新图片（如果提供了）
        if (request.imageUrls() != null) {
            productImageRepository.deleteByProductId(productId);
            saveProductImages(productId, request.imageUrls());
        }

        // 查询最新图片
        List<ProductImageEntity> images = productImageRepository
                .findByProductIdOrderBySortOrderAsc(productId);

        log.info("商品更新成功: productId={}", productId);

        // 如果名称、描述或标签有变化，异步通知 AI Service 更新向量索引
        boolean nameChanged = request.name() != null && !request.name().equals(oldName);
        boolean descChanged = request.description() != null;
        boolean tagsChanged = request.tags() != null;
        if (nameChanged || descChanged || tagsChanged) {
            aiIndexNotifier.notifyProductUpdated(productId, buildIndexDescription(product));
        }

        return toProductResponse(product, images);
    }

    /**
     * 商家下架商品（逻辑删除）
     *
     * 下架成功后异步通知 AI Service 删除向量索引
     */
    @Transactional
    public ProductMutationResponse offSale(CurrentUser currentUser, String productId) {
        ProductEntity product = findProductOrThrow(productId);

        // 校验权限
        if (!product.getMerchantId().equals(currentUser.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权下架此商品");
        }

        // 逻辑删除
        product.setStatus("OFF_SALE");
        product.setUpdatedAt(OffsetDateTime.now());
        productRepository.save(product);

        log.info("商品下架成功: productId={}", productId);

        // 异步通知 AI Service 删除向量索引
        aiIndexNotifier.notifyProductDeleted(productId);

        return new ProductMutationResponse(productId, "OFF_SALE", "DELETE_PENDING");
    }

    /**
     * 商家补货（原子操作）
     */
    @Transactional
    public RestockResponse restock(CurrentUser currentUser, String productId, RestockRequest request) {
        ProductEntity product = findProductOrThrow(productId);

        // 校验权限
        if (!product.getMerchantId().equals(currentUser.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权为此商品补货");
        }

        // 校验补货数量
        if (request.quantity() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "补货数量必须大于0");
        }

        // 原子补货（传入当前时间，避免 CURRENT_TIMESTAMP 与 OffsetDateTime 类型不匹配）
        OffsetDateTime now = OffsetDateTime.now();
        int affected = productRepository.increaseStock(productId, request.quantity(), now);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "补货失败");
        }

        // 重新查询获取最新库存
        ProductEntity updated = findProductOrThrow(productId);

        log.info("商品补货成功: productId={}, quantity={}, newStock={}",
                productId, request.quantity(), updated.getStock());

        return new RestockResponse(productId, updated.getStock());
    }

    // ==================== 内部接口 / 兼容接口 ====================

    /**
     * 根据商品ID列表批量查询摘要信息（供推荐/搜索模块使用）
     */
    public List<ProductSummaryResponse> findSummariesByIds(List<String> productIds) {
         return findSummariesByIds(productIds, 0.9, "根据 AI 匹配结果推荐");
    }

    public List<ProductSummaryResponse> findSummariesByIds(List<String> productIds, Double score, String reason) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        return productIds.stream()
                .map(productRepository::findByProductId)
                .flatMap(optional -> optional.stream())
                .filter(ProductEntity::isOnSale)
                .map(this::toSummaryResponse)
                .map(summary -> withScoreAndReason(summary, score, reason))
                .collect(Collectors.toList());
    }

    public List<ProductSummaryResponse> topSellingSummaries(int limit, Double score, String reason) {
        int normalizedLimit = Math.min(Math.max(limit, 1), 100);
        Pageable pageable = PageRequest.of(
                0,
                normalizedLimit,
                Sort.by(Sort.Order.desc("sales"), Sort.Order.desc("rating"), Sort.Order.desc("createdAt"))
        );

        return productRepository.findByStatus("ON_SALE", pageable)
                .getContent()
                .stream()
                .map(this::toSummaryResponse)
                .map(summary -> withScoreAndReason(summary, score, reason))
                .collect(Collectors.toList());
    }   

    /**
     * 兼容方法 — 供 AI/推荐/搜索模块使用
     * 返回数据库中所有在售商品的摘要列表
     */
    public List<ProductSummaryResponse> sampleSummaries(Double score, String reason) {
        return topSellingSummaries(20, score, reason);
    }

    // ==================== 私有方法 ====================

    /**
     * 根据 productId 查找商品，不存在则抛出异常
     */
    private ProductEntity findProductOrThrow(String productId) {
        return productRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "商品不存在: " + productId));
    }

    /**
     * 保存商品图片列表
     */
    private void saveProductImages(String productId, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 0; i < imageUrls.size(); i++) {
            ProductImageEntity image = new ProductImageEntity();
            image.setProductId(productId);
            image.setImageUrl(imageUrls.get(i));
            image.setSortOrder(i);
            image.setCreatedAt(now);
            productImageRepository.save(image);
        }
    }

    /**
     * 将 ProductEntity 转换为 ProductSummaryResponse
     */
    private ProductSummaryResponse toSummaryResponse(ProductEntity product) {
        List<ProductImageEntity> images = productImageRepository
                .findByProductIdOrderBySortOrderAsc(product.getProductId());
        String firstImage = images.isEmpty() ? null : images.get(0).getImageUrl();

        List<String> tagList = parseTags(product.getTags());

        return new ProductSummaryResponse(
                product.getProductId(),
                product.getName(),
                product.getPrice().toPlainString(),
                product.getStock(),
                firstImage,
                "/api/v1/products/" + product.getProductId(),
                product.getSales(),
                product.getRating().doubleValue(),
                tagList,
                null,
                null
        );
    }

    /**
     * 将 ProductEntity 转换为 ProductResponse
     */
    private ProductResponse toProductResponse(ProductEntity product, List<ProductImageEntity> images) {
        List<String> imageUrls = images.stream()
                .map(ProductImageEntity::getImageUrl)
                .collect(Collectors.toList());

        List<String> tagList = parseTags(product.getTags());

        return new ProductResponse(
                product.getProductId(),
                product.getMerchantId(),
                product.getName(),
                product.getDescription(),
                product.getCategoryId(),
                product.getCategoryName(),
                product.getPrice().toPlainString(),
                product.getStock(),
                product.getSales(),
                product.getRating().doubleValue(),
                product.getStatus(),
                tagList,
                imageUrls,
                "/api/v1/products/" + product.getProductId(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    /**
     * 解析逗号分隔的标签字符串为 List
     */
    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 解析分类名称
     */
    private String resolveCategoryName(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return null;
        }
        return CATEGORY_NAMES.getOrDefault(categoryId, categoryId);
    }

    private String buildIndexDescription(ProductEntity product) {
        StringBuilder description = new StringBuilder();
        appendIndexPart(description, product.getName());
        appendIndexPart(description, product.getCategoryName());
        appendIndexPart(description, product.getDescription());
        appendIndexPart(description, product.getTags());
        return description.toString();
    }

    private void appendIndexPart(StringBuilder target, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!target.isEmpty()) {
            target.append("，");
        }
        target.append(value.trim());
    }

    /**
     * 构建排序对象
     */
    private Sort buildSort(String sortBy, String sortOrder) {
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = "createdAt";
        }

        if (!List.of("price", "createdAt", "sales", "rating").contains(sortBy)) {
            sortBy = "createdAt";
        }

        Sort.Direction direction = Sort.Direction.DESC;
        if ("asc".equalsIgnoreCase(sortOrder)) {
            direction = Sort.Direction.ASC;
        }

        return Sort.by(direction, sortBy);
    }

    private ProductSummaryResponse withScoreAndReason(ProductSummaryResponse source, Double score, String reason) {
        return new ProductSummaryResponse(
                source.productId(),
                source.name(),
                source.price(),
                source.stock(),
                source.imageUrl(),
                source.detailUrl(),
                source.sales(),
                source.rating(),
                source.tags(),
                score,
                reason
        );
    }
}
