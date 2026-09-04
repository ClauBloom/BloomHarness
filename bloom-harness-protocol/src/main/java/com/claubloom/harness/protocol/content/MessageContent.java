package com.claubloom.harness.protocol.content;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed interface representing message content items.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TextContent.class, name = "text"),
        @JsonSubTypes.Type(value = ImageContent.class, name = "image"),
        @JsonSubTypes.Type(value = ThinkingContent.class, name = "thinking"),
        @JsonSubTypes.Type(value = ToolCallContent.class, name = "toolCall")
})
public sealed interface MessageContent
        permits TextContent, ImageContent, ThinkingContent, ToolCallContent {

    /**
     * 匹配 type 字段的可区分多态属性值。
     */
    String type();
}
