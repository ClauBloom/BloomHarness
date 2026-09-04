package com.claubloom.harness.core.event;

import com.claubloom.harness.protocol.message.AssistantMessage;
import com.claubloom.harness.protocol.message.ToolResultMessage;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 当 a single Turn finishes 时触发的事件。
 */
public record TurnEndEvent(
        @JsonProperty(value = "type", defaultValue = "turn_end")
        String type,
        @JsonProperty(value = "message", required = true)
        AssistantMessage message,
        @JsonProperty(value = "toolResults", required = true)
        List<ToolResultMessage> toolResults
) implements AgentEvent {
    public TurnEndEvent(AssistantMessage message, List<ToolResultMessage> toolResults) {
        this("turn_end", message, toolResults);
    }
}
