package com.claubloom.harness.protocol.codec;

/**
 * 当解码出的值不是有效的协议消息时抛出。
 * 镜像 pi 的 protocol/src/codec.ts 中的 ProtocolValidationError。
 */
public class ProtocolValidationError extends RuntimeException {

    public ProtocolValidationError(String message) {
        super(message);
    }

    public ProtocolValidationError(String message, Throwable cause) {
        super(message, cause);
    }
}
