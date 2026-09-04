package com.claubloom.harness.core.event;

import com.claubloom.harness.protocol.message.AgentMessage;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 智能体消息生成完成时触发的事件。
 */
public record MessageEndEvent(
        @JsonProperty(value = "type", defaultValue = "message_end")
        String type,
        @JsonProperty(value = "message", required = true)
        AgentMessage message
) implements AgentEvent {
    public MessageEndEvent(AgentMessage message) {
        this("message_end", message);
    }
}
