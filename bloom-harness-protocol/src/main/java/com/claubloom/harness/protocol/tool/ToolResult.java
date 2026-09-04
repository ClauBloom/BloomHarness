package com.claubloom.harness.protocol.tool;

import com.claubloom.harness.protocol.content.MessageContent;
import com.claubloom.harness.protocol.content.TextContent;
import com.claubloom.harness.protocol.session.Usage;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Result returned by executing a tool.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolResult(
        @JsonProperty(value = "content", required = true)
        List<MessageContent> content,
        @JsonProperty(value = "isError", defaultValue = "false")
        boolean isError,
        @JsonProperty("usage")
        Usage usage,
        @JsonProperty("details")
        Object details
) {
    public static ToolResult success(String text) {
        return new ToolResult(List.of(new TextContent(text)), false, null, null);
    }

    public static ToolResult success(List<MessageContent> content) {
        return new ToolResult(content, false, null, null);
    }

    public static ToolResult error(String errorMessage) {
        return new ToolResult(List.of(new TextContent(errorMessage)), true, null, null);
    }

    public static ToolResult error(List<MessageContent> content) {
        return new ToolResult(content, true, null, null);
    }

    public String output() {
        if (content == null || content.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (MessageContent mc : content) {
            if (mc instanceof TextContent tc) {
                sb.append(tc.text());
            }
        }
        return sb.toString();
    }
}
