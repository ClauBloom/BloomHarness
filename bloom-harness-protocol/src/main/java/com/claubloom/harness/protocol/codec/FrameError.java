package com.claubloom.harness.protocol.codec;

/**
 * Thrown when a byte stream violates the length-prefixed frame format.
 * Mirrors pi's FrameError in protocol/src/framing.ts.
 */
public class FrameError extends RuntimeException {

    public FrameError(String message) {
        super(message);
    }
}
