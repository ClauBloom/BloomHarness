package com.claubloom.harness.protocol.envelope;

import com.claubloom.harness.protocol.result.ProtocolError;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 客户端握手失败时发送的服务器 hello 错误帧。
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
