package com.claubloom.harness.protocol.envelope;

import com.claubloom.harness.protocol.session.ServerSnapshot;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 服务器 hello 帧,在客户端 hello 之后发送。
 */
public record ServerHello(
        @JsonProperty(value = "type", defaultValue = "hello")
        String type,
        @JsonProperty(value = "version", defaultValue = "1")
        int version,
        @JsonProperty(value = "connectionId", required = true)
        String connectionId,
        @JsonProperty(value = "snapshot", required = true)
        ServerSnapshot snapshot
) implements ServerMessage {
    public ServerHello(String connectionId, ServerSnapshot snapshot) {
        this("hello", ServerSnapshot.PROTOCOL_VERSION, connectionId, snapshot);
    }
}
