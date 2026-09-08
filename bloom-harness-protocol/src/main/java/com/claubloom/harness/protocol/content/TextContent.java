package com.claubloom.harness.protocol.content;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 文本消息内容条目。
 */
public record TextContent(
        @JsonProperty(value = "type", defaultValue = "text")
        String type,
        @JsonProperty(value = "text", required = true)
        String text
) implements MessageContent {

    public TextContent(String text) {
        this("text", text);
    }
}
