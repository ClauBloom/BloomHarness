package com.claubloom.harness.protocol.envelope;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 连接建立时客户端发送的第一个帧。
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
