package com.claubloom.harness.protocol.message;

import com.claubloom.harness.protocol.content.MessageContent;
import com.claubloom.harness.protocol.content.TextContent;
import com.claubloom.harness.protocol.model.ModelRef;
import com.claubloom.harness.protocol.session.Usage;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Assistant transcript message item with streaming / complete / error status.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssistantMessage(
        @JsonProperty(value = "id", required = true)
        String id,
        @JsonProperty(value = "role", defaultValue = "assistant")
        String role,
        @JsonProperty(value = "content", required = true)
        List<MessageContent> content,
        @JsonProperty(value = "model", required = true)
        ModelRef model,
        @JsonProperty("responseModel")
        String responseModel,
        @JsonProperty("usage")
        Usage usage,
        @JsonProperty(value = "timestamp", required = true)
        long timestamp,
        @JsonProperty(value = "status", required = true)
        String status, // 状态: streaming (流式生成中), complete (已完成), error (异常), aborted (已中断)
        @JsonProperty("stopReason")
        String stopReason, // 停止原因: stop (自然结束), length (长度超限), toolUse (触发工具调用), error (异常), aborted (中断)
        @JsonProperty("errorMessage")
        String errorMessage
) implements AgentMessage {

    public AssistantMessage(String id, List<MessageContent> content, ModelRef model, String responseModel,
                            Usage usage, long timestamp, String status, String stopReason, String errorMessage) {
        this(id, "assistant", content, model, responseModel, usage, timestamp, status, stopReason, errorMessage);
    }

    public static AssistantMessage complete(String id, List<MessageContent> content, ModelRef model,
                                           String responseModel, Usage usage, long timestamp, String stopReason) {
        return new AssistantMessage(id, "assistant", content, model, responseModel, usage, timestamp, "complete", stopReason, null);
    }

    public static AssistantMessage text(String text) {
        return new AssistantMessage(
                java.util.UUID.randomUUID().toString(),
                "assistant",
                List.of(new TextContent(text)),
                null,
                null,
                Usage.zero(),
                System.currentTimeMillis(),
                "complete",
                "stop",
                null
        );
    }

    public static AssistantMessage of(String text) {
        return text(text);
    }

    public static AssistantMessage error(String id, List<MessageContent> content, ModelRef model,
                                        Usage usage, long timestamp, String errorMessage) {
        return new AssistantMessage(id, "assistant", content, model, null, usage, timestamp, "error", "error", errorMessage);
    }
}
