package com.claubloom.harness.protocol.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * 一次工具调用的描述信息。
 */
public record ToolCall(
        @JsonProperty(value = "toolCallId", required = true)
        String toolCallId,
        @JsonProperty(value = "toolName", required = true)
        String toolName,
        @JsonProperty("input")
        Object input
) {
    public static ToolCall of(String toolCallId, String toolName, Object input) {
        return new ToolCall(toolCallId, toolName, input);
    }
}
