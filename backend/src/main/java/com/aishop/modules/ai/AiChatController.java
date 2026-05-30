package com.aishop.modules.ai;

import com.aishop.common.response.ApiResponse;
import com.aishop.modules.ai.dto.ChatMessageRequest;
import com.aishop.modules.ai.dto.ChatMessageResponse;
import com.aishop.modules.ai.dto.ChatSessionResponse;
import com.aishop.modules.ai.dto.ClearChatHistoryResponse;
import com.aishop.modules.ai.dto.CreateChatSessionRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/chat")
public class AiChatController {
    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping("/sessions")
    public ApiResponse<ChatSessionResponse> createSession(@RequestBody CreateChatSessionRequest request) {
        return ApiResponse.ok(aiChatService.createSession(request));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResponse<ChatMessageResponse> sendMessage(
            @PathVariable String sessionId,
            @Valid @RequestBody ChatMessageRequest request
    ) {
        return ApiResponse.ok(aiChatService.sendMessage(sessionId, request));
    }

    @DeleteMapping("/sessions/{sessionId}/history")
    public ApiResponse<ClearChatHistoryResponse> clearHistory(@PathVariable String sessionId) {
        return ApiResponse.ok(aiChatService.clearHistory(sessionId));
    }
}

