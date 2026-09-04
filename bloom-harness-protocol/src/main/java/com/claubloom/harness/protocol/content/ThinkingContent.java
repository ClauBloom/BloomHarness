package com.claubloom.harness.protocol.content;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Thinking message content item (CoT).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThinkingContent(
        @JsonProperty(value = "type", defaultValue = "thinking")
        String type,
        @JsonProperty(value = "thinking", required = true)
        String thinking,
        @JsonProperty("redacted")
        Boolean redacted
) implements MessageContent {

    public ThinkingContent(String thinking) {
        this("thinking", thinking, null);
    }

    public ThinkingContent(String thinking, Boolean redacted) {
        this("thinking", thinking, redacted);
    }
}
