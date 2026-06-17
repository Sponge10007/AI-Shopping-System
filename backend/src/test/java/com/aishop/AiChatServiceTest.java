package com.aishop;

import com.aishop.infrastructure.ai.AiChatResult;
import com.aishop.infrastructure.ai.AiServiceClient;
import com.aishop.modules.ai.AiChatService;
import com.aishop.modules.ai.dto.ChatMessageRequest;
import com.aishop.modules.ai.dto.ChatMessageResponse;
import com.aishop.modules.behavior.BehaviorService;
import com.aishop.modules.product.ProductService;
import com.aishop.modules.product.dto.ProductSummaryResponse;
import com.aishop.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {
    @Mock
    private ProductService productService;
    @Mock
    private AiServiceClient aiServiceClient;
    @Mock
    private BehaviorService behaviorService;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private AiChatService aiChatService;

    @BeforeEach
    void setUp() {
        aiChatService = new AiChatService(
                productService,
                aiServiceClient,
                behaviorService,
                jdbcTemplate,
                new ObjectMapper()
        );
    }

    @Test
    void sendMessageSanitizesUnsafeAiHtmlAndExtractsRelatedProducts() {
        AiChatResult unsafeResult = new AiChatResult(
                "<p onclick=\"alert(1)\">try this</p><script>alert(1)</script>"
                        + "<a href=\"javascript:alert(2)\">bad</a>",
                List.of("https://example.com/products/10001/main.jpg", "ftp://bad.example/image.jpg"),
                List.of("/products/10001", "javascript:alert(1)"),
                "raw-answer"
        );
        when(aiServiceClient.chat("u10001", "s10001", "find headphones")).thenReturn(unsafeResult);
        when(productService.findSummariesByIds(eq(List.of("10001")), eq(0.9), anyString()))
                .thenReturn(List.of(TestFixtures.productSummary("10001")));

        ChatMessageResponse response = aiChatService.sendMessage(
                "s10001",
                new ChatMessageRequest("find headphones")
        );

        assertThat(response.answer())
                .doesNotContain("<script")
                .doesNotContain("javascript:")
                .doesNotContain("onclick");
        assertThat(response.linkList()).containsExactly("/products/10001");
        assertThat(response.imageList()).containsExactly("https://example.com/products/10001/main.jpg");
        assertThat(response.relatedProducts()).extracting(ProductSummaryResponse::productId).containsExactly("10001");
        verify(behaviorService).recordForUser(eq("u10001"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sendMessageReturnsReadableFallbackWhenAiIsUnavailable() {
        when(aiServiceClient.chat("u10001", "s10001", "hello")).thenReturn(AiChatResult.unavailable());
        when(productService.findSummariesByIds(eq(List.of()), eq(0.9), anyString())).thenReturn(List.of());

        ChatMessageResponse response = aiChatService.sendMessage("s10001", new ChatMessageRequest("hello"));

        assertThat(response.answer()).contains("AI");
        assertThat(response.linkList()).isEmpty();
        assertThat(response.relatedProducts()).isEmpty();
    }
}
