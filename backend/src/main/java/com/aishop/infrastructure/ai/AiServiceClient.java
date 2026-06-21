package com.aishop.infrastructure.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class AiServiceClient {
    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);
    private static final double DEFAULT_DISTANCE_THRESHOLD = 0.9;

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiServiceClient(RestClient aiRestClient) {
        this.restClient = aiRestClient;
    }

    public boolean health() {
        try {
            AiEnvelope<Map<String, Object>> response = restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return response != null && response.ok();
        } catch (RestClientException exception) {
            return false;
        }
    }

    public List<String> semanticSearch(String userId, String query, Double distanceThreshold, Integer limit) {
        Map<String, Object> body = Map.of(
                "user_id", userId == null || userId.isBlank() ? "-1" : userId,
                "query", query,
                "distance_threshold", distanceThreshold == null ? DEFAULT_DISTANCE_THRESHOLD : distanceThreshold,
                "limit", normalizeLimit(limit, 50)
        );
        return postForData("/internal/v1/ai/search/products", body, new ParameterizedTypeReference<List<String>>() {
        }, List.of());
    }

    public AiImageSearchResult imageSearch(String userId, String imagePathOrUrl, Integer limit, Double distanceThreshold) {
        Map<String, Object> body = Map.of(
                "user_id", userId == null || userId.isBlank() ? "-1" : userId,
                "image_path_or_url", imagePathOrUrl,
                "distance_threshold", distanceThreshold == null ? DEFAULT_DISTANCE_THRESHOLD : distanceThreshold,
                "limit", normalizeLimit(limit, 20)
        );
        return postForData("/internal/v1/ai/search/image", body, new ParameterizedTypeReference<AiImageSearchResult>() {
        }, AiImageSearchResult.empty());
    }

    public List<String> recommendProducts(String userId, Integer maxnum) {
        try {
            AiEnvelope<List<String>> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/v1/ai/users/{userId}/recommendations")
                            .queryParam("maxnum", normalizeLimit(maxnum, 5))
                            .build(userId == null || userId.isBlank() ? "-1" : userId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null || !response.ok() || response.data() == null) {
                return List.of();
            }
            return response.data();
        } catch (RestClientException exception) {
            return List.of();
        }
    }

    public AiChatResult chat(String userId, String sessionId, String content) {
        Map<String, Object> body = Map.of(
                "user_id", userId,
                "session_id", sessionId,
                "content", content
        );
        try {
            AiEnvelope<Map<String, Object>> response = restClient.post()
                    .uri("/internal/v1/ai/chat/messages")
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null || !response.ok() || response.data() == null) {
                return AiChatResult.unavailable();
            }
            return mapChatResult(response.data());
        } catch (RestClientException exception) {
            return AiChatResult.unavailable();
        }
    }

    public AiChatResult streamChat(
            String userId,
            String sessionId,
            String content,
            Consumer<String> onDelta
    ) {
        Map<String, Object> body = Map.of(
                "user_id", userId,
                "session_id", sessionId,
                "content", content
        );
        try {
            return restClient.post()
                    .uri("/internal/v1/ai/chat/messages/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.parseMediaType("application/x-ndjson"))
                    .body(body)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new IllegalStateException(
                                    "AI Service stream returned HTTP " + response.getStatusCode().value()
                            );
                        }
                        AiChatResult result = null;
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8)
                        )) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.isBlank()) {
                                    continue;
                                }
                                JsonNode event = objectMapper.readTree(line);
                                String type = event.path("type").asText();
                                if ("delta".equals(type)) {
                                    String delta = event.path("content").asText("");
                                    if (!delta.isEmpty()) {
                                        onDelta.accept(delta);
                                    }
                                } else if ("done".equals(type)) {
                                    Map<String, Object> data = objectMapper.convertValue(
                                            event.path("data"),
                                            new TypeReference<>() {
                                            }
                                    );
                                    result = mapChatResult(data);
                                } else if ("error".equals(type)) {
                                    throw new IllegalStateException(
                                            event.path("message").asText("AI 流式响应失败")
                                    );
                                }
                            }
                        }
                        return result == null ? AiChatResult.unavailable() : result;
                    });
        } catch (Exception exception) {
            log.warn("AI Service streaming chat failed: error={}", exception.getMessage());
            return AiChatResult.unavailable();
        }
    }

    public AiCompareResult compareProducts(
            String userId,
            String intent,
            List<AiCompareProductInput> products
    ) {
        List<Map<String, Object>> serializedProducts = products.stream()
                .map(product -> Map.<String, Object>of(
                        "product_id", product.productId(),
                        "name", product.name(),
                        "description", product.description() == null ? "" : product.description(),
                        "category", product.category() == null ? "" : product.category(),
                        "price", product.price(),
                        "stock", product.stock(),
                        "sales", product.sales(),
                        "rating", product.rating(),
                        "tags", product.tags() == null ? List.of() : product.tags()
                ))
                .toList();
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("user_id", userId);
        // 兼容内部服务使用不同 Jackson 命名策略的旧运行实例。
        body.put("userId", userId);
        body.put("intent", intent);
        body.put("products", serializedProducts);
        return postForData(
                "/internal/v1/ai/compare/products",
                body,
                new ParameterizedTypeReference<AiCompareResult>() {
                },
                null
        );
    }

    public boolean deleteChatHistory(String userId, String sessionId) {
        try {
            AiEnvelope<Map<String, Object>> response = restClient.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/v1/ai/chat/history")
                            .queryParam("user_id", userId)
                            .queryParam("session_id", sessionId)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return response != null && response.ok();
        } catch (RestClientException exception) {
            return false;
        }
    }

    public boolean indexProduct(String productId, String description) {
        Map<String, Object> body = Map.of("description", description);
        try {
            AiEnvelope<Map<String, Object>> response = restClient.post()
                    .uri("/internal/v1/ai/products/{productId}/index", productId)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return response != null && response.ok();
        } catch (RestClientException exception) {
            return false;
        }
    }

    public boolean deleteProductIndex(String productId) {
        try {
            AiEnvelope<Map<String, Object>> response = restClient.delete()
                    .uri("/internal/v1/ai/products/{productId}/index", productId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return response != null && response.ok();
        } catch (RestClientException exception) {
            return false;
        }
    }

    private <T> T postForData(
            String path,
            Map<String, Object> body,
            ParameterizedTypeReference<T> dataType,
            T fallback
    ) {
        try {
            AiEnvelope<T> response = restClient.post()
                    .uri(path)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedAiEnvelopeType<>(dataType));
            if (response == null || !response.ok() || response.data() == null) {
                log.warn(
                        "AI Service returned no usable data: path={}, ok={}, error={}",
                        path,
                        response == null ? null : response.ok(),
                        response == null ? "empty response" : response.error()
                );
                return fallback;
            }
            return response.data();
        } catch (RestClientException exception) {
            log.warn(
                    "AI Service request failed: path={}, error={}",
                    path,
                    exception.getMessage()
            );
            return fallback;
        }
    }

    private static AiChatResult mapChatResult(Map<String, Object> data) {
        Object answer = data.get("answer");
        Object rawAnswer = data.get("raw_answer");
        return new AiChatResult(
                answer instanceof String text && !text.isBlank()
                        ? text
                        : "AI 助手暂时不可用，请稍后再试。",
                stringList(data.get("image_list")),
                stringList(data.get("link_list")),
                rawAnswer instanceof String text ? text : null
        );
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        return items.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    private static int normalizeLimit(Integer limit, int defaultLimit) {
        if (limit == null || limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, 100);
    }

    private static final class ParameterizedAiEnvelopeType<T> extends ParameterizedTypeReference<AiEnvelope<T>> {
        private final ParameterizedTypeReference<T> dataType;

        private ParameterizedAiEnvelopeType(ParameterizedTypeReference<T> dataType) {
            this.dataType = dataType;
        }

        @Override
        public java.lang.reflect.Type getType() {
            return org.springframework.core.ResolvableType
                    .forClassWithGenerics(AiEnvelope.class, org.springframework.core.ResolvableType.forType(dataType.getType()))
                    .getType();
        }
    }
}
