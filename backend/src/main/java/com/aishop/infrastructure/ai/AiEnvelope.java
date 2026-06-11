package com.aishop.infrastructure.ai;

public record AiEnvelope<T>(
        boolean ok,
        T data,
        String error,
        String service
) {
}
