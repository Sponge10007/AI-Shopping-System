package com.aishop.modules.ai;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.security.CurrentUser;
import com.aishop.infrastructure.ai.AiChatResult;
import com.aishop.infrastructure.ai.AiServiceClient;
import com.aishop.modules.ai.dto.ChatMessageRequest;
import com.aishop.modules.ai.dto.ChatMessageResponse;
import com.aishop.modules.ai.dto.ChatHistoryMessageResponse;
import com.aishop.modules.ai.dto.ChatSessionResponse;
import com.aishop.modules.ai.dto.ClearChatHistoryResponse;
import com.aishop.modules.ai.dto.CreateChatSessionRequest;
import com.aishop.modules.behavior.BehaviorService;
import com.aishop.modules.behavior.dto.BehaviorEventRequest;
import com.aishop.modules.product.ProductService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiChatService {
    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);
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

    public ChatSessionResponse createSession(CurrentUser currentUser, CreateChatSessionRequest request) {
        String title = request.title() == null || request.title().isBlank() ? "新的导购会话" : request.title();
        String sessionId = "s" + UUID.randomUUID().toString().replace("-", "");
        OffsetDateTime createdAt = OffsetDateTime.now();
        try {
            jdbcTemplate.update("""
                            INSERT INTO chat_sessions (session_id, user_id, title, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    sessionId,
                    currentUser.userId(),
                    title,
                    createdAt,
                    createdAt);
        } catch (DataAccessException exception) {
            log.warn("AI 会话保存失败: userId={}, error={}",
                    currentUser.userId(), exception.getMessage());
        }
        return new ChatSessionResponse(sessionId, title, createdAt);
    }

    public List<ChatSessionResponse> listSessions(CurrentUser currentUser) {
        try {
            return jdbcTemplate.query("""
                            SELECT session_id, title, created_at
                            FROM chat_sessions
                            WHERE user_id = ?
                            ORDER BY updated_at DESC, created_at DESC
                            """,
                    (resultSet, rowNum) -> new ChatSessionResponse(
                            resultSet.getString("session_id"),
                            resultSet.getString("title"),
                            resultSet.getObject("created_at", OffsetDateTime.class)
                    ),
                    currentUser.userId());
        } catch (DataAccessException exception) {
            log.warn("AI 会话列表读取失败: userId={}, error={}",
                    currentUser.userId(), exception.getMessage());
            return List.of();
        }
    }

    public List<ChatHistoryMessageResponse> listMessages(
            CurrentUser currentUser,
            String sessionId
    ) {
        String userId = currentUser.userId();
        ensureSessionExists(sessionId, userId, false);
        try {
            return jdbcTemplate.query("""
                            SELECT role, content, image_list, link_list, created_at
                            FROM chat_messages
                            WHERE session_id = ?
                              AND user_id = ?
                            ORDER BY created_at ASC, id ASC
                            """,
                    (resultSet, rowNum) -> {
                        List<String> imageList = fromJsonList(resultSet.getString("image_list"));
                        List<String> linkList = fromJsonList(resultSet.getString("link_list"));
                        return new ChatHistoryMessageResponse(
                                normalizeRole(resultSet.getString("role")),
                                resultSet.getString("content"),
                                imageList,
                                linkList,
                                productService.findSummariesByIds(
                                        extractProductIds(linkList),
                                        0.9,
                                        "AI 助手历史推荐"
                                ),
                                resultSet.getObject("created_at", OffsetDateTime.class)
                        );
                    },
                    sessionId,
                    userId);
        } catch (DataAccessException exception) {
            log.warn("AI 消息历史读取失败: sessionId={}, userId={}, error={}",
                    sessionId, userId, exception.getMessage());
            return List.of();
        }
    }

    public ChatMessageResponse sendMessage(CurrentUser currentUser, String sessionId, ChatMessageRequest request) {
        String userId = currentUser.userId();
        ensureSessionExists(sessionId, userId, true);
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

    public void streamMessage(
            CurrentUser currentUser,
            String sessionId,
            ChatMessageRequest request,
            OutputStream outputStream
    ) throws IOException {
        String userId = currentUser.userId();
        ensureSessionExists(sessionId, userId, true);
        saveMessage(sessionId, userId, "USER", request.content(), List.of(), List.of(), null);

        AiChatResult aiResult = aiServiceClient.streamChat(
                userId,
                sessionId,
                request.content(),
                delta -> writeStreamEventUnchecked(
                        outputStream,
                        Map.of("type", "delta", "content", delta)
                )
        );
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

        writeStreamEvent(outputStream, Map.of(
                "type", "done",
                "data", new ChatMessageResponse(
                        sessionId,
                        safeAnswer,
                        imageList,
                        linkList,
                        aiResult.rawAnswer(),
                        productService.findSummariesByIds(
                                extractProductIds(linkList),
                                0.9,
                                "AI 助手结合你的需求推荐"
                        )
                )
        ));
    }

    public ClearChatHistoryResponse clearHistory(CurrentUser currentUser, String sessionId) {
        String userId = currentUser.userId();
        ensureSessionExists(sessionId, userId, false);
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

    private void ensureSessionExists(String sessionId, String userId, boolean createIfMissing) {
        try {
            String ownerId = jdbcTemplate.queryForObject(
                    "SELECT user_id FROM chat_sessions WHERE session_id = ?",
                    String.class,
                    sessionId);
            if (userId.equals(ownerId)) {
                return;
            }
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问此 AI 对话会话");
        } catch (EmptyResultDataAccessException exception) {
            if (!createIfMissing) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "AI 对话会话不存在");
            }
            jdbcTemplate.update("""
                            INSERT INTO chat_sessions (session_id, user_id, title)
                            VALUES (?, ?, ?)
                            """,
                    sessionId,
                    userId,
                    "新的导购会话");
        } catch (BusinessException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            log.warn("AI 会话校验失败: sessionId={}, userId={}, error={}",
                    sessionId, userId, exception.getMessage());
            if (!createIfMissing) {
                throw exception;
            }
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
            log.warn("AI 消息保存失败: sessionId={}, userId={}, role={}, error={}",
                    sessionId, userId, role, exception.getMessage());
        }
        touchSession(sessionId, userId, role, content);
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

    private List<String> fromJsonList(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    rawJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private String normalizeRole(String role) {
        return "ASSISTANT".equalsIgnoreCase(role) ? "assistant" : "user";
    }

    private void touchSession(String sessionId, String userId, String role, String content) {
        try {
            if ("USER".equals(role)) {
                jdbcTemplate.update("""
                                UPDATE chat_sessions
                                SET updated_at = now(),
                                    title = CASE
                                      WHEN title IN ('新对话', '新的导购会话')
                                      THEN ?
                                      ELSE title
                                    END
                                WHERE session_id = ?
                                  AND user_id = ?
                                """,
                        summarizeTitle(content),
                        sessionId,
                        userId);
            } else {
                jdbcTemplate.update("""
                                UPDATE chat_sessions
                                SET updated_at = now()
                                WHERE session_id = ?
                                  AND user_id = ?
                                """,
                        sessionId,
                        userId);
            }
        } catch (DataAccessException exception) {
            log.warn("AI 会话更新时间失败: sessionId={}, error={}",
                    sessionId, exception.getMessage());
        }
    }

    private String summarizeTitle(String content) {
        String normalized = content == null ? "新的导购会话" : content.strip();
        if (normalized.isBlank()) {
            return "新的导购会话";
        }
        return normalized.length() <= 24 ? normalized : normalized.substring(0, 24) + "…";
    }

    private void writeStreamEvent(OutputStream outputStream, Object event) throws IOException {
        outputStream.write(objectMapper.writeValueAsBytes(event));
        outputStream.write('\n');
        outputStream.flush();
    }

    private void writeStreamEventUnchecked(OutputStream outputStream, Object event) {
        try {
            writeStreamEvent(outputStream, event);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
