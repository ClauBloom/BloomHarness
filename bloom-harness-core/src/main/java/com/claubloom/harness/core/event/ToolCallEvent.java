package com.claubloom.harness.core.event;

import com.claubloom.harness.protocol.tool.ToolCall;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event fired before a tool call is executed.
 */
public record ToolCallEvent(
        @JsonProperty(value = "type", defaultValue = "tool_call")
        String type,
        @JsonProperty(value = "toolCall", required = true)
        ToolCall toolCall
) implements AgentEvent {
    public ToolCallEvent(ToolCall toolCall) {
        this("tool_call", toolCall);
    }
}
