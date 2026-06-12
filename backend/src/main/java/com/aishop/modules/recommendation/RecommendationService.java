package com.aishop.modules.recommendation;

import com.aishop.common.security.CurrentUser;
import com.aishop.infrastructure.ai.AiServiceClient;
import com.aishop.modules.product.ProductService;
import com.aishop.modules.product.dto.ProductSummaryResponse;
import com.aishop.modules.recommendation.dto.HomeRecommendationResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RecommendationService {
    private final ProductService productService;
    private final AiServiceClient aiServiceClient;

    public RecommendationService(ProductService productService, AiServiceClient aiServiceClient) {
        this.productService = productService;
        this.aiServiceClient = aiServiceClient;
    }

    public HomeRecommendationResponse homeRecommendations(Integer limit) {
        int normalizedLimit = normalizeLimit(limit, 20);
        String userId = CurrentUser.prototypeCustomer().userId();
        List<String> productIds = aiServiceClient.recommendProducts(userId, normalizedLimit);
        List<ProductSummaryResponse> items = productService.findSummariesByIds(
                productIds,
                0.91,
                "根据你的搜索和浏览偏好推荐"
        );
        if (!items.isEmpty()) {
            return new HomeRecommendationResponse("USER_PROFILE", items.stream().limit(normalizedLimit).toList());
        }
        return new HomeRecommendationResponse(
                "HOT_PRODUCTS",
                productService.topSellingSummaries(normalizedLimit, 0.75, "热门商品推荐")
        );
    }

    private int normalizeLimit(Integer limit, int defaultLimit) {
        if (limit == null || limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, 100);
    }
}
