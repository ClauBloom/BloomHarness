package com.claubloom.harness.protocol.content;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 助手消息中的工具调用消息内容条目。
 */
public record ToolCallContent(
        @JsonProperty(value = "type", defaultValue = "toolCall")
        String type,
        @JsonProperty(value = "toolCallId", required = true)
        String toolCallId,
        @JsonProperty(value = "toolName", required = true)
        String toolName,
        @JsonProperty("input")
        Object input
) implements MessageContent {

    public ToolCallContent(String toolCallId, String toolName, Object input) {
        this("toolCall", toolCallId, toolName, input);
    }
}
