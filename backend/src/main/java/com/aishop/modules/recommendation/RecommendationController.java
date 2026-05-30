package com.aishop.modules.recommendation;

import com.aishop.common.response.ApiResponse;
import com.aishop.modules.recommendation.dto.HomeRecommendationResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/home")
    public ApiResponse<HomeRecommendationResponse> homeRecommendations(
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        return ApiResponse.ok(recommendationService.homeRecommendations(limit));
    }
}

