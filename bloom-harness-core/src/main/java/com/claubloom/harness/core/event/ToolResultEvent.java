package com.claubloom.harness.core.event;

import com.claubloom.harness.protocol.tool.ToolCall;
import com.claubloom.harness.protocol.tool.ToolResult;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 在工具执行完成并产生结果后触发的事件。
 */
public record ToolResultEvent(
        @JsonProperty(value = "type", defaultValue = "tool_result")
        String type,
        @JsonProperty(value = "toolCall", required = true)
        ToolCall toolCall,
        @JsonProperty(value = "result", required = true)
        ToolResult result
) implements AgentEvent {
    public ToolResultEvent(ToolCall toolCall, ToolResult result) {
        this("tool_result", toolCall, result);
    }
}
