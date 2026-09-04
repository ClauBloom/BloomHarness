package com.claubloom.harness.core.event;

import com.claubloom.harness.protocol.message.AgentMessage;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event fired when an AgentMessage begins processing/streaming.
 */
public record MessageStartEvent(
        @JsonProperty(value = "type", defaultValue = "message_start")
        String type,
        @JsonProperty(value = "message", required = true)
        AgentMessage message
) implements AgentEvent {
    public MessageStartEvent(AgentMessage message) {
        this("message_start", message);
    }
}
