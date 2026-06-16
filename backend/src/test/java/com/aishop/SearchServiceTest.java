package com.aishop;

import com.aishop.infrastructure.ai.AiImageSearchResult;
import com.aishop.infrastructure.ai.AiServiceClient;
import com.aishop.modules.behavior.BehaviorService;
import com.aishop.modules.behavior.dto.BehaviorEventRequest;
import com.aishop.modules.product.ProductService;
import com.aishop.modules.product.dto.ProductSummaryResponse;
import com.aishop.modules.search.SearchService;
import com.aishop.modules.search.dto.ImageSearchResponse;
import com.aishop.modules.search.dto.SearchFilters;
import com.aishop.modules.search.dto.SemanticSearchRequest;
import com.aishop.modules.search.dto.SemanticSearchResponse;
import com.aishop.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {
    @Mock
    private ProductService productService;
    @Mock
    private AiServiceClient aiServiceClient;
    @Mock
    private BehaviorService behaviorService;

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(productService, aiServiceClient, behaviorService);
    }

    @Test
    void semanticSearchUsesAiIdsAppliesFiltersAndRecordsBehavior() {
        ProductSummaryResponse matching = TestFixtures.productSummary("10001", "299.00", 10);
        ProductSummaryResponse outOfStock = TestFixtures.productSummary("10002", "199.00", 0);
        ProductSummaryResponse tooExpensive = TestFixtures.productSummary("10003", "999.00", 5);
        List<String> aiIds = List.of("10001", "10002", "10003");

        when(aiServiceClient.semanticSearch("u10001", "headphones", 0.9, 50)).thenReturn(aiIds);
        when(productService.findSummariesByIds(eq(aiIds), eq(0.93), anyString()))
                .thenReturn(List.of(matching, outOfStock, tooExpensive));

        SemanticSearchResponse response = searchService.semanticSearch(new SemanticSearchRequest(
                "headphones",
                new SearchFilters(null, new BigDecimal("100.00"), new BigDecimal("500.00"), true),
                0.9,
                2
        ));

        assertThat(response.relaxed()).isFalse();
        assertThat(response.items()).extracting(ProductSummaryResponse::productId).containsExactly("10001");

        ArgumentCaptor<BehaviorEventRequest> eventCaptor = ArgumentCaptor.forClass(BehaviorEventRequest.class);
        verify(behaviorService).recordForUser(eq("u10001"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("SEARCH");
        assertThat(eventCaptor.getValue().query()).isEqualTo("headphones");
    }

    @Test
    void semanticSearchFallsBackToHotProductsWhenAiFindsNothing() {
        when(aiServiceClient.semanticSearch("u10001", "rare item", null, 50)).thenReturn(List.of());
        when(productService.findSummariesByIds(eq(List.of()), eq(0.93), anyString())).thenReturn(List.of());
        when(productService.topSellingSummaries(eq(3), eq(0.70), anyString()))
                .thenReturn(List.of(TestFixtures.productSummary("10001"), TestFixtures.productSummary("10002")));

        SemanticSearchResponse response = searchService.semanticSearch(new SemanticSearchRequest(
                "rare item",
                null,
                null,
                3
        ));

        assertThat(response.relaxed()).isTrue();
        assertThat(response.items()).extracting(ProductSummaryResponse::productId)
                .containsExactly("10001", "10002");
    }

    @Test
    void imageSearchUsesDetectedKeywordsAndProductIds() {
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "headphones.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
        AiImageSearchResult aiResult = new AiImageSearchResult(
                List.of("headphones", "black"),
                "headphones black",
                List.of("10001")
        );

        when(aiServiceClient.imageSearch("u10001", "search-upload://headphones.png", 2, 0.9))
                .thenReturn(aiResult);
        when(productService.findSummariesByIds(eq(List.of("10001")), eq(0.88), anyString()))
                .thenReturn(List.of(TestFixtures.productSummary("10001")));

        ImageSearchResponse response = searchService.imageSearch(file, 2);

        assertThat(response.detectedObject()).isEqualTo("headphones");
        assertThat(response.items()).extracting(ProductSummaryResponse::productId).containsExactly("10001");
        verify(behaviorService).recordForUser(eq("u10001"), org.mockito.ArgumentMatchers.any());
    }
}
