package com.claubloom.harness.protocol.codec;

/**
 * Thrown when a decoded value is not a valid protocol message.
 * Mirrors pi's ProtocolValidationError in protocol/src/codec.ts.
 */
public class ProtocolValidationError extends RuntimeException {

    public ProtocolValidationError(String message) {
        super(message);
    }

    public ProtocolValidationError(String message, Throwable cause) {
        super(message, cause);
    }
}
