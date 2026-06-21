package com.aishop.infrastructure.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-service")
public record AiServiceProperties(
        String baseUrl,
        int connectTimeoutMs,
        int readTimeoutMs
) {
    public AiServiceProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://127.0.0.1:9000";
        }
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = 1000;
        }
        if (readTimeoutMs <= 0) {
            readTimeoutMs = 30000;
        }
    }
}
