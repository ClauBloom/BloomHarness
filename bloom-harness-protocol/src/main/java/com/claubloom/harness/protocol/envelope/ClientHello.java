package com.claubloom.harness.protocol.envelope;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * First frame sent by client upon connection.
 */
public record ClientHello(
        @JsonProperty(value = "type", defaultValue = "hello")
        String type,
        @JsonProperty(value = "version", defaultValue = "1")
        int version
) implements ClientMessage {
    public ClientHello() {
        this("hello", 1);
    }

    public ClientHello(int version) {
        this("hello", version);
    }
}
