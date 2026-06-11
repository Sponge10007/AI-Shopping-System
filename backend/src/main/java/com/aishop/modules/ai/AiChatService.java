package com.aishop.modules.ai;

import com.aishop.common.security.CurrentUser;
import com.aishop.infrastructure.ai.AiChatResult;
import com.aishop.infrastructure.ai.AiServiceClient;
import com.aishop.modules.ai.dto.ChatMessageRequest;
import com.aishop.modules.ai.dto.ChatMessageResponse;
import com.aishop.modules.ai.dto.ChatSessionResponse;
import com.aishop.modules.ai.dto.ClearChatHistoryResponse;
import com.aishop.modules.ai.dto.CreateChatSessionRequest;
import com.aishop.modules.behavior.BehaviorService;
import com.aishop.modules.behavior.dto.BehaviorEventRequest;
import com.aishop.modules.product.ProductService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiChatService {
    private static final Pattern SCRIPT_BLOCK = Pattern.compile("(?is)<script.*?>.*?</script>");
    private static final Pattern UNSAFE_TAG = Pattern.compile("(?is)<(?!/?(?:a|img|br|p|strong|em)\\b)[^>]*>");
    private static final Pattern EVENT_HANDLER = Pattern.compile("(?i)\\s+on[a-z]+\\s*=\\s*(['\"]).*?\\1");
    private static final Pattern JAVASCRIPT_URL = Pattern.compile("(?i)javascript:");
    private static final Pattern PRODUCT_LINK = Pattern.compile("/products/([A-Za-z0-9_-]+)");

    private final ProductService productService;
    private final AiServiceClient aiServiceClient;
    private final BehaviorService behaviorService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AiChatService(
            ProductService productService,
            AiServiceClient aiServiceClient,
            BehaviorService behaviorService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.productService = productService;
        this.aiServiceClient = aiServiceClient;
        this.behaviorService = behaviorService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public ChatSessionResponse createSession(CreateChatSessionRequest request) {
        String title = request.title() == null || request.title().isBlank() ? "新的导购会话" : request.title();
        String sessionId = "s" + UUID.randomUUID().toString().replace("-", "");
        OffsetDateTime createdAt = OffsetDateTime.now();
        try {
            jdbcTemplate.update("""
                            INSERT INTO chat_sessions (session_id, user_id, title, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    sessionId,
                    CurrentUser.prototypeCustomer().userId(),
                    title,
                    createdAt,
                    createdAt);
        } catch (DataAccessException exception) {
            // 数据库未启动时仍返回可用于前端联调的 session_id。
        }
        return new ChatSessionResponse(sessionId, title, createdAt);
    }

    public ChatMessageResponse sendMessage(String sessionId, ChatMessageRequest request) {
        String userId = CurrentUser.prototypeCustomer().userId();
        ensureSessionExists(sessionId, userId);
        saveMessage(sessionId, userId, "USER", request.content(), List.of(), List.of(), null);

        AiChatResult aiResult = aiServiceClient.chat(userId, sessionId, request.content());
        String safeAnswer = sanitizeHtml(aiResult.answer());
        List<String> imageList = safeList(aiResult.imageList());
        List<String> linkList = safeList(aiResult.linkList());
        saveMessage(sessionId, userId, "ASSISTANT", safeAnswer, imageList, linkList, aiResult.rawAnswer());
        behaviorService.recordForUser(userId, new BehaviorEventRequest(
                "CHAT",
                null,
                request.content(),
                Map.of("session_id", sessionId)
        ));

        return new ChatMessageResponse(
                sessionId,
                safeAnswer,
                imageList,
                linkList,
                aiResult.rawAnswer(),
                productService.findSummariesByIds(extractProductIds(linkList), 0.9, "AI 助手结合你的需求推荐")
        );
    }

    public ClearChatHistoryResponse clearHistory(String sessionId) {
        String userId = CurrentUser.prototypeCustomer().userId();
        try {
            jdbcTemplate.update("""
                            DELETE FROM chat_messages
                            WHERE session_id = ?
                              AND user_id = ?
                            """,
                    sessionId,
                    userId);
        } catch (DataAccessException exception) {
            // 允许 Python 清理继续执行。
        }
        boolean aiCleared = aiServiceClient.deleteChatHistory(userId, sessionId);
        return new ClearChatHistoryResponse(aiCleared);
    }

    private void ensureSessionExists(String sessionId, String userId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM chat_sessions WHERE session_id = ? AND user_id = ?",
                    Integer.class,
                    sessionId,
                    userId);
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.update("""
                            INSERT INTO chat_sessions (session_id, user_id, title)
                            VALUES (?, ?, ?)
                            ON CONFLICT (session_id) DO NOTHING
                            """,
                    sessionId,
                    userId,
                    "新的导购会话");
        } catch (DataAccessException exception) {
            // 原型鉴权阶段允许无库联调。
        }
    }

    private void saveMessage(
            String sessionId,
            String userId,
            String role,
            String content,
            List<String> imageList,
            List<String> linkList,
            String rawAnswer
    ) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO chat_messages
                              (session_id, user_id, role, content, image_list, link_list, raw_answer)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                    sessionId,
                    userId,
                    role,
                    content,
                    toJson(imageList),
                    toJson(linkList),
                    rawAnswer);
        } catch (DataAccessException exception) {
            // 聊天记录失败不阻断 AI 回复。
        }
    }

    private String sanitizeHtml(String answer) {
        if (answer == null || answer.isBlank()) {
            return "AI 助手暂时不可用，请稍后再试。";
        }
        String cleaned = SCRIPT_BLOCK.matcher(answer).replaceAll("");
        cleaned = UNSAFE_TAG.matcher(cleaned).replaceAll("");
        cleaned = EVENT_HANDLER.matcher(cleaned).replaceAll("");
        cleaned = JAVASCRIPT_URL.matcher(cleaned).replaceAll("");
        return cleaned;
    }

    private List<String> safeList(List<String> value) {
        if (value == null) {
            return List.of();
        }
        return value.stream()
                .filter(item -> item != null && !item.isBlank())
                .filter(item -> item.startsWith("https://") || item.startsWith("/"))
                .toList();
    }

    private List<String> extractProductIds(List<String> links) {
        return links.stream()
                .map(PRODUCT_LINK::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .distinct()
                .toList();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }
}
