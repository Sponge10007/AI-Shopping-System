package com.aishop.modules.ai;

import com.aishop.modules.ai.dto.ChatMessageRequest;
import com.aishop.modules.ai.dto.ChatMessageResponse;
import com.aishop.modules.ai.dto.ChatSessionResponse;
import com.aishop.modules.ai.dto.ClearChatHistoryResponse;
import com.aishop.modules.ai.dto.CreateChatSessionRequest;
import com.aishop.modules.product.ProductService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class AiChatService {
    private final ProductService productService;

    public AiChatService(ProductService productService) {
        this.productService = productService;
    }

    public ChatSessionResponse createSession(CreateChatSessionRequest request) {
        String title = request.title() == null || request.title().isBlank() ? "新的导购会话" : request.title();
        return new ChatSessionResponse("s10001", title, OffsetDateTime.now());
    }

    public ChatMessageResponse sendMessage(String sessionId, ChatMessageRequest request) {
        return new ChatMessageResponse(
                sessionId,
                "这里是智能导购的示例回复。后续该位置将调用 Python AI Service，并在返回前完成内容过滤。",
                productService.sampleSummaries(0.9, "AI 助手结合你的需求推荐")
        );
    }

    public ClearChatHistoryResponse clearHistory(String sessionId) {
        return new ClearChatHistoryResponse(true);
    }
}

