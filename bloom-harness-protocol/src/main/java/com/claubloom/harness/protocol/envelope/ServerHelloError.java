package com.claubloom.harness.protocol.envelope;

import com.claubloom.harness.protocol.result.ProtocolError;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server hello error frame sent when client handshake fails.
 */
public record ServerHelloError(
        @JsonProperty(value = "type", defaultValue = "hello_error")
        String type,
        @JsonProperty(value = "error", required = true)
        ProtocolError error
) implements ServerMessage {
    public ServerHelloError(ProtocolError error) {
        this("hello_error", error);
    }
}
