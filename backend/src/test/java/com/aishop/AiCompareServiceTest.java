package com.aishop;

import com.aishop.common.security.CurrentUser;
import com.aishop.infrastructure.ai.AiCompareDimensionResult;
import com.aishop.infrastructure.ai.AiCompareItemResult;
import com.aishop.infrastructure.ai.AiCompareProductInput;
import com.aishop.infrastructure.ai.AiCompareResult;
import com.aishop.infrastructure.ai.AiServiceClient;
import com.aishop.modules.ai.AiCompareService;
import com.aishop.modules.ai.dto.CompareProductsRequest;
import com.aishop.modules.ai.dto.CompareProductsResponse;
import com.aishop.modules.behavior.BehaviorService;
import com.aishop.modules.product.ProductService;
import com.aishop.modules.product.dto.ProductResponse;
import com.aishop.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCompareServiceTest {
    private static final CurrentUser CUSTOMER = new CurrentUser("u20002", "CUSTOMER");

    @Mock
    private ProductService productService;
    @Mock
    private AiServiceClient aiServiceClient;
    @Mock
    private BehaviorService behaviorService;

    private AiCompareService service;

    @BeforeEach
    void setUp() {
        service = new AiCompareService(productService, aiServiceClient, behaviorService);
    }

    @Test
    void returnsStructuredAiResultWhenModelResponseIsValid() {
        ProductResponse first = TestFixtures.product("10001");
        ProductResponse second = product("10002", "399.00", 4.6, 180);
        when(productService.getProduct("10001")).thenReturn(first);
        when(productService.getProduct("10002")).thenReturn(second);
        when(aiServiceClient.compareProducts(eq("u20002"), eq("通勤"), any()))
                .thenReturn(new AiCompareResult(
                        "10001",
                        "第一款更适合通勤",
                        List.of("价格更低"),
                        List.of(
                                new AiCompareItemResult("10001", 92, "更均衡", List.of("便携"), List.of("无")),
                                new AiCompareItemResult("10002", 78, "音质优先", List.of("评分高"), List.of("价格高"))
                        ),
                        List.of(new AiCompareDimensionResult(
                                "价格优势",
                                Map.of("10001", 95, "10002", 70)
                        ))
                ));

        CompareProductsResponse response = service.compare(
                CUSTOMER,
                new CompareProductsRequest(List.of("10001", "10002"), "通勤")
        );

        assertThat(response.source()).isEqualTo("AI");
        assertThat(response.winnerProductId()).isEqualTo("10001");
        assertThat(response.items()).hasSize(2);
        assertThat(response.dimensions().get(0).scores()).containsEntry("10001", 95);
        verify(behaviorService).recordForUser(eq("u20002"), any());
    }

    @Test
    void fallsBackToRealProductDataWhenAiIsUnavailable() {
        when(productService.getProduct("10001")).thenReturn(TestFixtures.product("10001"));
        when(productService.getProduct("10002")).thenReturn(product("10002", "599.00", 4.2, 20));
        when(aiServiceClient.compareProducts(eq("u20002"), any(), any())).thenReturn(null);

        CompareProductsResponse response = service.compare(
                CUSTOMER,
                new CompareProductsRequest(List.of("10001", "10002"), "")
        );

        assertThat(response.source()).isEqualTo("RULE_BASED");
        assertThat(response.winnerProductId()).isIn("10001", "10002");
        assertThat(response.items()).allSatisfy(item ->
                assertThat(item.score()).isBetween(0, 100));
        assertThat(response.dimensions()).extracting(dimension -> dimension.name())
                .contains("价格优势", "用户口碑", "需求匹配");
    }

    private ProductResponse product(String id, String price, double rating, int sales) {
        ProductResponse base = TestFixtures.product(id);
        return new ProductResponse(
                base.productId(),
                base.merchantId(),
                "Product " + id,
                base.description(),
                base.categoryId(),
                base.categoryName(),
                price,
                base.stock(),
                sales,
                rating,
                base.status(),
                base.tags(),
                base.imageUrls(),
                base.detailUrl(),
                base.createdAt(),
                base.updatedAt()
        );
    }
}
