package com.claubloom.harness.protocol.envelope;

import com.claubloom.harness.protocol.session.ServerSnapshot;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server hello frame sent after client hello.
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
