package com.aishop.modules.product;

import com.aishop.common.response.PageResponse;
import com.aishop.modules.product.dto.CreateProductRequest;
import com.aishop.modules.product.dto.ProductMutationResponse;
import com.aishop.modules.product.dto.ProductResponse;
import com.aishop.modules.product.dto.ProductSummaryResponse;
import com.aishop.modules.product.dto.RestockRequest;
import com.aishop.modules.product.dto.RestockResponse;
import com.aishop.modules.product.dto.UpdateProductRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ProductService {

    public PageResponse<ProductSummaryResponse> listProducts(int page, int size) {
        List<ProductSummaryResponse> items = sampleSummaries(null, null);
        return PageResponse.of(items, page, size, items.size());
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
        return productIds.stream()
                .map(productId -> sampleSummary(productId, 0.9, "根据 AI 匹配结果推荐"))
                .toList();
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
