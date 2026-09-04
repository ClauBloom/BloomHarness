package com.claubloom.harness.core.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event fired when a message streaming chunk updates (text/thinking/toolCall).
 */
public record MessageUpdateEvent(
        @JsonProperty(value = "type", defaultValue = "message_update")
        String type,
        @JsonProperty(value = "messageId", required = true)
        String messageId,
        @JsonProperty(value = "contentIndex", required = true)
        int contentIndex,
        @JsonProperty(value = "kind", required = true)
        String kind, // text, thinking, toolCall
        @JsonProperty(value = "delta", required = true)
        String delta
) implements AgentEvent {
    public MessageUpdateEvent(String messageId, int contentIndex, String kind, String delta) {
        this("message_update", messageId, contentIndex, kind, delta);
    }
}
