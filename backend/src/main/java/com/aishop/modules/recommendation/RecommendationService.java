package com.aishop.modules.recommendation;

import com.aishop.modules.product.ProductService;
import com.aishop.modules.recommendation.dto.HomeRecommendationResponse;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {
    private final ProductService productService;

    public RecommendationService(ProductService productService) {
        this.productService = productService;
    }

    public HomeRecommendationResponse homeRecommendations(Integer limit) {
        return new HomeRecommendationResponse(
                "USER_PROFILE",
                productService.sampleSummaries(0.91, "根据你的搜索和浏览偏好推荐")
        );
    }
}

