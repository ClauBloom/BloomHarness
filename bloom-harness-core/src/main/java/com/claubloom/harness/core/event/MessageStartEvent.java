package com.claubloom.harness.core.event;

import com.claubloom.harness.protocol.message.AgentMessage;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 当 AgentMessage 开始处理/流式传输时触发的事件。
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
