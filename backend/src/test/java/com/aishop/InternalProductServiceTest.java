package com.aishop;

import com.aishop.modules.internal.InternalProductService;
import com.aishop.modules.internal.dto.BatchProductAiSummaryResponse;
import com.aishop.modules.internal.dto.ProductAiSummaryResponse;
import com.aishop.modules.product.ProductService;
import com.aishop.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalProductServiceTest {
    @Mock
    private ProductService productService;

    private InternalProductService internalProductService;

    @BeforeEach
    void setUp() {
        internalProductService = new InternalProductService(productService);
    }

    @Test
    void aiSummaryContainsOnlyProductFacingSummaryFields() {
        when(productService.getProduct("10001")).thenReturn(TestFixtures.product("10001"));

        ProductAiSummaryResponse response = internalProductService.getAiSummary("10001");

        assertThat(response.productId()).isEqualTo("10001");
        assertThat(response.summaryText())
                .contains("Noise Cancelling Headphones")
                .contains("299.00")
                .contains("120")
                .contains("https://example.com/products/10001")
                .doesNotContain("m10001")
                .doesNotContain("merchant");
    }

    @Test
    void batchAiSummariesPreserveRequestedIds() {
        when(productService.getProduct("10001")).thenReturn(TestFixtures.product("10001"));
        when(productService.getProduct("10002")).thenReturn(TestFixtures.product("10002"));

        BatchProductAiSummaryResponse response = internalProductService.getAiSummaries(List.of("10001", "10002"));

        assertThat(response.items()).extracting(ProductAiSummaryResponse::productId)
                .containsExactly("10001", "10002");
    }
}
