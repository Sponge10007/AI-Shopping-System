package com.aishop.modules.ai;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.response.ApiResponse;
import com.aishop.common.security.CurrentUser;
import com.aishop.modules.ai.dto.ChatMessageRequest;
import com.aishop.modules.ai.dto.ChatMessageResponse;
import com.aishop.modules.ai.dto.ChatHistoryMessageResponse;
import com.aishop.modules.ai.dto.ChatSessionResponse;
import com.aishop.modules.ai.dto.ClearChatHistoryResponse;
import com.aishop.modules.ai.dto.CreateChatSessionRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/chat")
public class AiChatController {
    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping("/sessions")
    public ApiResponse<ChatSessionResponse> createSession(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody CreateChatSessionRequest request) {
        requireUser(currentUser);
        return ApiResponse.ok(aiChatService.createSession(currentUser, request));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionResponse>> listSessions(
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        requireUser(currentUser);
        return ApiResponse.ok(aiChatService.listSessions(currentUser));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatHistoryMessageResponse>> listMessages(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable String sessionId
    ) {
        requireUser(currentUser);
        return ApiResponse.ok(aiChatService.listMessages(currentUser, sessionId));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResponse<ChatMessageResponse> sendMessage(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable String sessionId,
            @Valid @RequestBody ChatMessageRequest request
    ) {
        requireUser(currentUser);
        return ApiResponse.ok(aiChatService.sendMessage(currentUser, sessionId, request));
    }

    @PostMapping(
            value = "/sessions/{sessionId}/messages/stream",
            produces = "application/x-ndjson"
    )
    public StreamingResponseBody streamMessage(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable String sessionId,
            @Valid @RequestBody ChatMessageRequest request
    ) {
        requireUser(currentUser);
        return outputStream -> aiChatService.streamMessage(
                currentUser,
                sessionId,
                request,
                outputStream
        );
    }

    @DeleteMapping("/sessions/{sessionId}/history")
    public ApiResponse<ClearChatHistoryResponse> clearHistory(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable String sessionId) {
        requireUser(currentUser);
        return ApiResponse.ok(aiChatService.clearHistory(currentUser, sessionId));
    }

    private void requireUser(CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
    }
}

