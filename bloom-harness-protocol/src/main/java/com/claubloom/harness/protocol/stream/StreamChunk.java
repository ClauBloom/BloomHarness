package com.claubloom.harness.protocol.stream;

import com.claubloom.harness.protocol.message.AgentMessage;
import com.claubloom.harness.protocol.session.Usage;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 用于 SSE 或 WebSocket 流式传输的统一分片载荷。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StreamChunk(
        @JsonProperty(value = "type", required = true)
        String type, // text, thinking, tool_call, message_start, message_end, error
        @JsonProperty("messageId")
        String messageId,
        @JsonProperty("contentIndex")
        Integer contentIndex,
        @JsonProperty("delta")
        String delta,
        @JsonProperty("item")
        AgentMessage item,
        @JsonProperty("usage")
        Usage usage,
        @JsonProperty("errorMessage")
        String errorMessage
) {
    public static StreamChunk textDelta(String messageId, int index, String delta) {
        return new StreamChunk("text", messageId, index, delta, null, null, null);
    }

    public static StreamChunk thinkingDelta(String messageId, int index, String delta) {
        return new StreamChunk("thinking", messageId, index, delta, null, null, null);
    }

    public static StreamChunk item(String type, AgentMessage item) {
        return new StreamChunk(type, item.id(), null, null, item, null, null);
    }

    public static StreamChunk usage(Usage usage) {
        return new StreamChunk("usage", null, null, null, null, usage, null);
    }

    public static StreamChunk error(String errorMessage) {
        return new StreamChunk("error", null, null, null, null, null, errorMessage);
    }
}
