package com.claubloom.harness.protocol.codec;

/**
 * 当字节流违反带长度前缀的帧格式时抛出。
 */
public class FrameError extends RuntimeException {

    public FrameError(String message) {
        super(message);
    }
}
