package com.aishop;

import com.aishop.common.security.CurrentUser;
import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.infrastructure.ai.AiImageSearchResult;
import com.aishop.infrastructure.ai.AiServiceClient;
import com.aishop.modules.behavior.BehaviorService;
import com.aishop.modules.behavior.dto.BehaviorEventRequest;
import com.aishop.modules.product.ProductService;
import com.aishop.modules.product.dto.ProductResponse;
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
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {
    private static final CurrentUser CUSTOMER = new CurrentUser("u20002", "CUSTOMER");

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
        ProductResponse matching = product(
                "10001", "蓝牙降噪耳机", "适合通勤", "c_headphone",
                "耳机", "299.00", 10, List.of("蓝牙", "降噪")
        );
        ProductResponse outOfStock = product(
                "10002", "真无线耳机", "适合通勤", "c_headphone",
                "耳机", "199.00", 0, List.of("真无线", "便携")
        );
        ProductResponse tooExpensive = product(
                "10003", "高端监听耳机", "专业监听", "c_headphone",
                "耳机", "999.00", 5, List.of("监听")
        );
        List<String> aiIds = List.of("10001", "10002", "10003");

        when(aiServiceClient.semanticSearch("u20002", "headphones", 0.9, 50)).thenReturn(aiIds);
        when(productService.searchableProducts(500))
                .thenReturn(List.of(matching, outOfStock, tooExpensive));

        SemanticSearchResponse response = searchService.semanticSearch(CUSTOMER, new SemanticSearchRequest(
                "headphones",
                new SearchFilters(null, new BigDecimal("100.00"), new BigDecimal("500.00"), true),
                0.9,
                2
        ));

        assertThat(response.relaxed()).isFalse();
        assertThat(response.items()).extracting(ProductSummaryResponse::productId).containsExactly("10001");

        ArgumentCaptor<BehaviorEventRequest> eventCaptor = ArgumentCaptor.forClass(BehaviorEventRequest.class);
        verify(behaviorService).recordForUser(eq("u20002"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("SEARCH");
        assertThat(eventCaptor.getValue().targetId()).isEqualTo("headphones");
    }

    @Test
    void semanticSearchFallsBackToHotProductsWhenAiFindsNothing() {
        when(aiServiceClient.semanticSearch("u20002", "rare item", null, 50)).thenReturn(List.of());
        when(productService.searchableProducts(500)).thenReturn(List.of());
        when(productService.topSellingSummaries(eq(3), eq(0.70), anyString()))
                .thenReturn(List.of(TestFixtures.productSummary("10001"), TestFixtures.productSummary("10002")));

        SemanticSearchResponse response = searchService.semanticSearch(CUSTOMER, new SemanticSearchRequest(
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
    void hybridSearchUsesProductTypeAsHardConstraintWhenVectorRecallIsWrong() {
        ProductResponse cup = product(
                "10002", "智能保温杯", "支持温度显示，便携防漏", "c_home",
                "家居", "129.00", 80, List.of("办公", "保温", "防漏")
        );
        ProductResponse backpack = product(
                "10003", "轻量运动背包", "适合健身和短途出行", "c_outdoor",
                "户外", "189.00", 64, List.of("运动", "收纳")
        );

        when(aiServiceClient.semanticSearch(
                eq("u20002"),
                eq("办公室喝热水的防漏杯子"),
                eq(0.9),
                eq(50)
        )).thenReturn(List.of("10003"));
        when(productService.searchableProducts(500)).thenReturn(List.of(backpack, cup));

        SemanticSearchResponse response = searchService.semanticSearch(CUSTOMER, new SemanticSearchRequest(
                "办公室喝热水的防漏杯子",
                null,
                0.9,
                10
        ));

        assertThat(response.relaxed()).isFalse();
        assertThat(response.items()).extracting(ProductSummaryResponse::productId)
                .containsExactly("10002");
        assertThat(response.items().get(0).reason()).contains("家居", "防漏");
    }

    @Test
    void hybridSearchInfersBudgetAndFindsUnindexedCatalogProduct() {
        ProductResponse lamp = product(
                "10008", "护眼学习台灯", "支持亮度调节与多种色温", "c_home",
                "家居", "239.00", 67, List.of("护眼", "阅读", "调光")
        );
        ProductResponse expensiveLamp = product(
                "10010", "专业阅读台灯", "桌面阅读灯", "c_home",
                "家居", "599.00", 20, List.of("阅读", "调光")
        );
        ProductResponse headphones = product(
                "10001", "蓝牙降噪耳机", "适合通勤", "c_headphone",
                "耳机", "299.00", 100, List.of("蓝牙", "降噪")
        );

        when(aiServiceClient.semanticSearch(
                eq("u20002"),
                eq("预算300元以内，晚上看书用的护眼可调光台灯"),
                eq(0.9),
                eq(50)
        )).thenReturn(List.of("10001"));
        when(productService.searchableProducts(500))
                .thenReturn(List.of(headphones, expensiveLamp, lamp));

        SemanticSearchResponse response = searchService.semanticSearch(CUSTOMER, new SemanticSearchRequest(
                "预算300元以内，晚上看书用的护眼可调光台灯",
                null,
                0.9,
                10
        ));

        assertThat(response.items()).extracting(ProductSummaryResponse::productId)
                .containsExactly("10008");
        assertThat(new BigDecimal(response.items().get(0).price()))
                .isLessThanOrEqualTo(new BigDecimal("300.00"));
    }

    @Test
    void imageSearchUsesDetectedKeywordsAndProductIds() {
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "headphones.png",
                "image/png",
                new byte[]{
                        (byte) 0x89, 0x50, 0x4E, 0x47,
                        0x0D, 0x0A, 0x1A, 0x0A
                }
        );
        AiImageSearchResult aiResult = new AiImageSearchResult(
                List.of("headphones", "black"),
                "headphones black",
                List.of("10001")
        );

        when(aiServiceClient.imageSearch(
                eq("u20002"),
                org.mockito.ArgumentMatchers.startsWith("data:image/png;base64,"),
                eq(2),
                eq(0.9)
        ))
                .thenReturn(aiResult);
        when(productService.findSummariesByIds(eq(List.of("10001")), eq(0.88), anyString()))
                .thenReturn(List.of(TestFixtures.productSummary("10001")));

        ImageSearchResponse response = searchService.imageSearch(CUSTOMER, file, 2);

        assertThat(response.detectedObject()).isEqualTo("headphones");
        assertThat(response.items()).extracting(ProductSummaryResponse::productId).containsExactly("10001");
        verify(behaviorService).recordForUser(eq("u20002"), org.mockito.ArgumentMatchers.any());
        verify(aiServiceClient).imageSearch(
                eq("u20002"),
                org.mockito.ArgumentMatchers.startsWith("data:image/png;base64,iVBORw0KGgo="),
                eq(2),
                eq(0.9)
        );
    }

    @Test
    void imageSearchRejectsContentThatDoesNotMatchDeclaredType() {
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "fake.png",
                "image/png",
                "not an image".getBytes()
        );

        assertThatThrownBy(() -> searchService.imageSearch(CUSTOMER, file, 2))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE));
    }

    private ProductResponse product(
            String productId,
            String name,
            String description,
            String categoryId,
            String categoryName,
            String price,
            int stock,
            List<String> tags
    ) {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-23T00:00:00Z");
        return new ProductResponse(
                productId,
                "m10001",
                name,
                description,
                categoryId,
                categoryName,
                price,
                stock,
                100,
                4.7,
                "ON_SALE",
                tags,
                List.of("https://example.com/" + productId + ".jpg"),
                "/api/v1/products/" + productId,
                now,
                now
        );
    }
}
