package com.claubloom.harness.protocol.message;

import com.claubloom.harness.protocol.content.MessageContent;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * User transcript message item.
 */
public record UserMessage(
        @JsonProperty(value = "id", required = true)
        String id,
        @JsonProperty(value = "role", defaultValue = "user")
        String role,
        @JsonProperty(value = "content", required = true)
        List<MessageContent> content,
        @JsonProperty(value = "timestamp", required = true)
        long timestamp
) implements AgentMessage {

    public UserMessage(String id, List<MessageContent> content, long timestamp) {
        this(id, "user", content, timestamp);
    }

    public static UserMessage text(String text) {
        return new UserMessage(
                java.util.UUID.randomUUID().toString(),
                List.of(new com.claubloom.harness.protocol.content.TextContent(text)),
                System.currentTimeMillis()
        );
    }

    public static UserMessage of(String text) {
        return text(text);
    }
}
