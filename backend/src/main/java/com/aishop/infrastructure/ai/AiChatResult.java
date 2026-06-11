package com.aishop.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiChatResult(
        String answer,
        @JsonProperty("image_list")
        List<String> imageList,
        @JsonProperty("link_list")
        List<String> linkList,
        @JsonProperty("raw_answer")
        String rawAnswer
) {
    public static AiChatResult unavailable() {
        return new AiChatResult(
                "AI 助手暂时不可用，请稍后再试。",
                List.of(),
                List.of(),
                null
        );
    }
}
