package com.aishop;

import com.aishop.common.security.CurrentUser;
import com.aishop.infrastructure.ai.AiServiceClient;
import com.aishop.modules.product.ProductService;
import com.aishop.modules.recommendation.RecommendationService;
import com.aishop.modules.recommendation.dto.HomeRecommendationResponse;
import com.aishop.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {
    @Mock
    private ProductService productService;
    @Mock
    private AiServiceClient aiServiceClient;

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(productService, aiServiceClient);
    }

    @Test
    void authenticatedRecommendationsUseTheCurrentUser() {
        CurrentUser currentUser = new CurrentUser("u20002", "CUSTOMER");
        when(aiServiceClient.recommendProducts("u20002", 5)).thenReturn(List.of("10001"));
        when(productService.findSummariesByIds(eq(List.of("10001")), eq(0.91), anyString()))
                .thenReturn(List.of(TestFixtures.productSummary("10001")));

        HomeRecommendationResponse response =
                recommendationService.homeRecommendations(currentUser, 5);

        assertThat(response.strategy()).isEqualTo("USER_PROFILE");
        verify(aiServiceClient).recommendProducts("u20002", 5);
    }

    @Test
    void anonymousRecommendationsUseTheNonProfileIdentity() {
        when(aiServiceClient.recommendProducts("-1", 5)).thenReturn(List.of());
        when(productService.findSummariesByIds(eq(List.of()), eq(0.91), anyString()))
                .thenReturn(List.of());
        when(productService.topSellingSummaries(eq(5), eq(0.75), anyString()))
                .thenReturn(List.of(TestFixtures.productSummary("10001")));

        HomeRecommendationResponse response =
                recommendationService.homeRecommendations(null, 5);

        assertThat(response.strategy()).isEqualTo("HOT_PRODUCTS");
        verify(aiServiceClient).recommendProducts("-1", 5);
    }
}
