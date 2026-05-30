package com.aishop.modules.internal;

import com.aishop.modules.internal.dto.BatchProductAiSummaryResponse;
import com.aishop.modules.internal.dto.ProductAiSummaryResponse;
import com.aishop.modules.product.ProductService;
import com.aishop.modules.product.dto.ProductResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InternalProductService {
    private final ProductService productService;

    public InternalProductService(ProductService productService) {
        this.productService = productService;
    }

    public ProductAiSummaryResponse getAiSummary(String productId) {
        ProductResponse product = productService.getProduct(productId);
        String summary = "商品名：" + product.name()
                + "；价格：" + product.price()
                + "元；库存：" + product.stock()
                + "；特点：" + String.join("、", product.tags())
                + "；网页链接：" + product.detailUrl();
        return new ProductAiSummaryResponse(productId, summary);
    }

    public BatchProductAiSummaryResponse getAiSummaries(List<String> productIds) {
        List<ProductAiSummaryResponse> items = productIds.stream()
                .map(this::getAiSummary)
                .toList();
        return new BatchProductAiSummaryResponse(items);
    }
}

