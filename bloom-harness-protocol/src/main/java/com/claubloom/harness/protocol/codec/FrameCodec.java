package com.claubloom.harness.protocol.codec;

/**
 * Static frame helpers for the length-prefixed wire format.
 * Faithful port of pi protocol/src/framing.ts constants and encodeFrame/assertCompleteFrame.
 */
public final class FrameCodec {

    /** 4 字节无符号大端序长度前缀。 */
    public static final int FRAME_HEADER_LENGTH = 4;
    private static final long MAX_UINT32 = 0xffff_ffffL;

    /** 单个数据帧载荷的默认上限：16 MiB（对齐 pi-agent 规范）。 */
    public static final int DEFAULT_MAX_FRAME_LENGTH = 16 * 1024 * 1024;

    private FrameCodec() {
    }

    /** Resolves and validates the effective frame length limit. */
    public static int resolveMaxFrameLength(Integer maxFrameLength) {
        int value = maxFrameLength != null ? maxFrameLength : DEFAULT_MAX_FRAME_LENGTH;
        if (value < 0 || value > MAX_UINT32) {
            throw new IllegalArgumentException(
                    "maxFrameLength must be an integer between 0 and " + MAX_UINT32);
        }
        return value;
    }

    /** Prefixes a payload with its unsigned 32-bit big-endian byte length. */
    public static byte[] encodeFrame(byte[] payload) {
        if (payload == null) throw new IllegalArgumentException("Frame payload must not be null");
        if (payload.length > MAX_UINT32) {
            throw new IllegalArgumentException("Frame payload exceeds the unsigned 32-bit length limit");
        }
        byte[] frame = new byte[FRAME_HEADER_LENGTH + payload.length];
        int length = payload.length;
        frame[0] = (byte) (length >>> 24);
        frame[1] = (byte) (length >>> 16);
        frame[2] = (byte) (length >>> 8);
        frame[3] = (byte) length;
        System.arraycopy(payload, 0, frame, FRAME_HEADER_LENGTH, payload.length);
        return frame;
    }

    /** Validates that bytes contain exactly one complete frame within the configured limit. */
    public static void assertCompleteFrame(byte[] frame, Integer maxFrameLength) {
        if (frame == null) throw new FrameError("Frame must not be null");
        if (frame.length < FRAME_HEADER_LENGTH) {
            throw new FrameError("Frame does not contain a complete length prefix");
        }
        long length = ((frame[0] & 0xffL) << 24)
                | ((frame[1] & 0xffL) << 16)
                | ((frame[2] & 0xffL) << 8)
                | (frame[3] & 0xffL);
        int limit = resolveMaxFrameLength(maxFrameLength);
        if (length > limit) {
            throw new FrameError("Frame length " + length + " exceeds configured limit of " + limit);
        }
        if (frame.length != FRAME_HEADER_LENGTH + length) {
            throw new FrameError("Frame must contain exactly one complete payload");
        }
    }

    /** 从数据帧头部提取载荷长度（必须至少包含 FRAME_HEADER_LENGTH 字节）。 */
    static long readLengthPrefix(byte[] header, int offset) {
        return ((header[offset] & 0xffL) << 24)
                | ((header[offset + 1] & 0xffL) << 16)
                | ((header[offset + 2] & 0xffL) << 8)
                | (header[offset + 3] & 0xffL);
    }
}
