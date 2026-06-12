package com.aishop.modules.product;

import com.aishop.common.response.PageResponse;
import com.aishop.modules.product.dto.CreateProductRequest;
import com.aishop.modules.product.dto.ProductMutationResponse;
import com.aishop.modules.product.dto.ProductResponse;
import com.aishop.modules.product.dto.ProductSummaryResponse;
import com.aishop.modules.product.dto.RestockRequest;
import com.aishop.modules.product.dto.RestockResponse;
import com.aishop.modules.product.dto.UpdateProductRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ProductService {
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public ProductService(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public PageResponse<ProductSummaryResponse> listProducts(int page, int size) {
        try {
            int normalizedPage = Math.max(page, 1);
            int normalizedSize = Math.min(Math.max(size, 1), 100);
            int offset = (normalizedPage - 1) * normalizedSize;
            List<ProductSummaryResponse> items = jdbcTemplate.query("""
                            SELECT p.product_id, p.name, p.price, p.stock, p.sales, p.rating, p.tags,
                                   COALESCE((
                                     SELECT pi.image_url FROM product_images pi
                                     WHERE pi.product_id = p.product_id
                                     ORDER BY pi.sort_order, pi.id
                                     LIMIT 1
                                   ), '') AS image_url
                            FROM products p
                            WHERE p.status = 'ON_SALE'
                            ORDER BY p.sales DESC, p.id DESC
                            LIMIT ? OFFSET ?
                            """,
                    this::mapSummary,
                    normalizedSize,
                    offset);
            Long total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM products WHERE status = 'ON_SALE'",
                    Long.class
            );
            return PageResponse.of(items, normalizedPage, normalizedSize, total == null ? items.size() : total);
        } catch (DataAccessException exception) {
            List<ProductSummaryResponse> items = sampleSummaries(null, null);
            return PageResponse.of(items, page, size, items.size());
        }
    }

    public ProductResponse getProduct(String productId) {
        return sampleProduct(productId);
    }

    public PageResponse<ProductResponse> listMerchantProducts(String status, int page, int size) {
        List<ProductResponse> items = List.of(sampleProduct("10001"));
        return PageResponse.of(items, page, size, items.size());
    }

    public ProductMutationResponse createProduct(CreateProductRequest request) {
        return new ProductMutationResponse("10001", "ON_SALE", "PENDING");
    }

    public ProductResponse updateProduct(String productId, UpdateProductRequest request) {
        ProductResponse product = sampleProduct(productId);
        return new ProductResponse(
                product.productId(),
                product.merchantId(),
                request.name() == null ? product.name() : request.name(),
                request.description() == null ? product.description() : request.description(),
                product.categoryId(),
                product.categoryName(),
                request.price() == null ? product.price() : request.price().toPlainString(),
                product.stock(),
                product.sales(),
                product.rating(),
                product.status(),
                request.tags() == null ? product.tags() : request.tags(),
                request.imageUrls() == null ? product.imageUrls() : request.imageUrls(),
                product.detailUrl(),
                product.createdAt(),
                OffsetDateTime.now()
        );
    }

    public RestockResponse restock(String productId, RestockRequest request) {
        return new RestockResponse(productId, 120 + request.quantity());
    }

    public ProductMutationResponse offSale(String productId) {
        return new ProductMutationResponse(productId, "OFF_SALE", "DELETE_PENDING");
    }

    public List<ProductSummaryResponse> findSummariesByIds(List<String> productIds) {
        return findSummariesByIds(productIds, 0.9, "根据 AI 匹配结果推荐");
    }

    public List<ProductSummaryResponse> findSummariesByIds(List<String> productIds, Double score, String reason) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        try {
            MapSqlParameterSource parameters = new MapSqlParameterSource("ids", productIds);
            List<ProductSummaryResponse> rows = namedParameterJdbcTemplate.query("""
                            SELECT p.product_id, p.name, p.price, p.stock, p.sales, p.rating, p.tags,
                                   COALESCE((
                                     SELECT pi.image_url FROM product_images pi
                                     WHERE pi.product_id = p.product_id
                                     ORDER BY pi.sort_order, pi.id
                                     LIMIT 1
                                   ), '') AS image_url
                            FROM products p
                            WHERE p.product_id IN (:ids)
                              AND p.status = 'ON_SALE'
                            """,
                    parameters,
                    (rs, rowNum) -> withScoreAndReason(mapSummary(rs, rowNum), score, reason));
            return preserveRequestedOrder(productIds, rows);
        } catch (DataAccessException exception) {
            return productIds.stream()
                    .map(productId -> sampleSummary(productId, score, reason))
                    .toList();
        }
    }

    public List<ProductSummaryResponse> topSellingSummaries(int limit, Double score, String reason) {
        try {
            int normalizedLimit = Math.min(Math.max(limit, 1), 100);
            List<ProductSummaryResponse> items = jdbcTemplate.query("""
                            SELECT p.product_id, p.name, p.price, p.stock, p.sales, p.rating, p.tags,
                                   COALESCE((
                                     SELECT pi.image_url FROM product_images pi
                                     WHERE pi.product_id = p.product_id
                                     ORDER BY pi.sort_order, pi.id
                                     LIMIT 1
                                   ), '') AS image_url
                            FROM products p
                            WHERE p.status = 'ON_SALE'
                            ORDER BY p.sales DESC, p.rating DESC, p.id DESC
                            LIMIT ?
                            """,
                    (rs, rowNum) -> withScoreAndReason(mapSummary(rs, rowNum), score, reason),
                    normalizedLimit);
            if (!items.isEmpty()) {
                return items;
            }
        } catch (DataAccessException ignored) {
        }
        return sampleSummaries(score, reason).stream().limit(Math.max(limit, 1)).toList();
    }

    public List<ProductSummaryResponse> sampleSummaries(Double score, String reason) {
        return List.of(
                sampleSummary("10001", score, reason),
                new ProductSummaryResponse(
                        "10002",
                        "智能保温杯",
                        "129.00",
                        80,
                        "https://example.com/products/10002/main.jpg",
                        "https://example.com/products/10002",
                        210,
                        4.6,
                        List.of("办公", "保温", "便携"),
                        score,
                        reason
                )
        );
    }

    private ProductSummaryResponse sampleSummary(String productId, Double score, String reason) {
        return new ProductSummaryResponse(
                productId,
                "蓝牙降噪耳机",
                "299.00",
                120,
                "https://example.com/products/" + productId + "/main.jpg",
                "https://example.com/products/" + productId,
                320,
                4.8,
                List.of("蓝牙", "降噪", "通勤"),
                score,
                reason
        );
    }

    private ProductSummaryResponse mapSummary(ResultSet rs, int rowNum) throws SQLException {
        String productId = rs.getString("product_id");
        String imageUrl = rs.getString("image_url");
        if (imageUrl == null || imageUrl.isBlank()) {
            imageUrl = "https://example.com/products/" + productId + "/main.jpg";
        }
        BigDecimal price = rs.getBigDecimal("price");
        BigDecimal rating = rs.getBigDecimal("rating");
        return new ProductSummaryResponse(
                productId,
                rs.getString("name"),
                price == null ? "0.00" : price.toPlainString(),
                rs.getInt("stock"),
                imageUrl,
                "https://example.com/products/" + productId,
                rs.getInt("sales"),
                rating == null ? 0.0 : rating.doubleValue(),
                splitTags(rs.getString("tags")),
                null,
                null
        );
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

    private List<ProductSummaryResponse> preserveRequestedOrder(
            List<String> productIds,
            List<ProductSummaryResponse> summaries
    ) {
        if (summaries.isEmpty()) {
            return List.of();
        }
        List<ProductSummaryResponse> ordered = new ArrayList<>();
        for (String productId : productIds) {
            for (ProductSummaryResponse summary : summaries) {
                if (summary.productId().equals(productId)) {
                    ordered.add(summary);
                    break;
                }
            }
        }
        return ordered;
    }

    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return Collections.emptyList();
        }
        return List.of(tags.split(","))
                .stream()
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .toList();
    }

    private ProductResponse sampleProduct(String productId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new ProductResponse(
                productId,
                "m10001",
                "蓝牙降噪耳机",
                "适合通勤和学习的主动降噪蓝牙耳机",
                "c_headphone",
                "耳机",
                "299.00",
                120,
                320,
                4.8,
                "ON_SALE",
                List.of("蓝牙", "降噪", "通勤"),
                List.of("https://example.com/products/" + productId + "/main.jpg"),
                "https://example.com/products/" + productId,
                now,
                now
        );
    }
}
