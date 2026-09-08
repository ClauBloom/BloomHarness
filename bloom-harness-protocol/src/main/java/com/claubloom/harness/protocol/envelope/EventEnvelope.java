package com.claubloom.harness.protocol.envelope;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 向已连接客户端广播服务器事件的事件信封。
 */
public record EventEnvelope(
        @JsonProperty(value = "type", defaultValue = "event")
        String type,
        @JsonProperty(value = "event", required = true)
        ServerEvent event
) implements ServerMessage {
    public EventEnvelope(ServerEvent event) {
        this("event", event);
    }
}
