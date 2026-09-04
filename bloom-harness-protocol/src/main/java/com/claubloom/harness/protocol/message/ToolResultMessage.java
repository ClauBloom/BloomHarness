package com.claubloom.harness.protocol.message;

import com.claubloom.harness.protocol.content.MessageContent;
import com.claubloom.harness.protocol.session.Usage;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Tool execution transcript item.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolResultMessage(
        @JsonProperty(value = "id", required = true)
        String id,
        @JsonProperty(value = "role", defaultValue = "tool")
        String role,
        @JsonProperty(value = "toolCallId", required = true)
        String toolCallId,
        @JsonProperty(value = "toolName", required = true)
        String toolName,
        @JsonProperty("input")
        Object input,
        @JsonProperty(value = "content", required = true)
        List<MessageContent> content,
        @JsonProperty("details")
        Object details,
        @JsonProperty("usage")
        Usage usage,
        @JsonProperty(value = "timestamp", required = true)
        long timestamp,
        @JsonProperty(value = "status", required = true)
        String status, // 运行状态: running (执行中), complete (已完成), error (失败)
        @JsonProperty(value = "isError", defaultValue = "false")
        boolean isError
) implements AgentMessage {

    public ToolResultMessage(String id, String toolCallId, String toolName, Object input,
                             List<MessageContent> content, Object details, Usage usage,
                             long timestamp, String status, boolean isError) {
        this(id, "tool", toolCallId, toolName, input, content, details, usage, timestamp, status, isError);
    }

    public static ToolResultMessage success(String id, String toolCallId, String toolName, Object input,
                                           List<MessageContent> content, Object details, Usage usage, long timestamp) {
        return new ToolResultMessage(id, "tool", toolCallId, toolName, input, content, details, usage, timestamp, "complete", false);
    }

    public static ToolResultMessage error(String id, String toolCallId, String toolName, Object input,
                                         List<MessageContent> content, Object details, Usage usage, long timestamp) {
        return new ToolResultMessage(id, "tool", toolCallId, toolName, input, content, details, usage, timestamp, "error", true);
    }
}
