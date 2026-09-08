package com.claubloom.harness.protocol.codec;

/**
 * 当解码出的值不是有效的协议消息时抛出。
 */
public class ProtocolValidationError extends RuntimeException {

    public ProtocolValidationError(String message) {
        super(message);
    }

    public ProtocolValidationError(String message, Throwable cause) {
        super(message, cause);
    }
}
