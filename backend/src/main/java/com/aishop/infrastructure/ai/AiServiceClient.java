package com.aishop.infrastructure.ai;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
public class AiServiceClient {
    private static final double DEFAULT_DISTANCE_THRESHOLD = 0.9;

    private final RestClient restClient;

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
        return postForData("/internal/v1/ai/chat/messages", body, new ParameterizedTypeReference<AiChatResult>() {
        }, AiChatResult.unavailable());
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
                return fallback;
            }
            return response.data();
        } catch (RestClientException exception) {
            return fallback;
        }
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
