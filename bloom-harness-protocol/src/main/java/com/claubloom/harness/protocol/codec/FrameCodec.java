package com.claubloom.harness.protocol.codec;

/**
 * 带长度前缀的二进制帧编码/校验工具类。
 * 提供 4 字节大端序长度前缀的帧编码、帧完整性断言以及帧长度上限解析。
 */
public final class FrameCodec {

    /** 4 字节无符号大端序长度前缀。 */
    public static final int FRAME_HEADER_LENGTH = 4;
    private static final long MAX_UINT32 = 0xffff_ffffL;

    /** 单个数据帧载荷的默认上限：16 MiB。 */
    public static final int DEFAULT_MAX_FRAME_LENGTH = 16 * 1024 * 1024;

    private FrameCodec() {
    }

    /** 解析并校验生效的帧长度上限。 */
    public static int resolveMaxFrameLength(Integer maxFrameLength) {
        int value = maxFrameLength != null ? maxFrameLength : DEFAULT_MAX_FRAME_LENGTH;
        if (value < 0 || value > MAX_UINT32) {
            throw new IllegalArgumentException(
                    "maxFrameLength must be an integer between 0 and " + MAX_UINT32);
        }
        return value;
    }

    /** 为负载加上其无符号 32 位大端字节长度前缀。 */
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

    /** 校验字节恰好包含一个完整帧且长度在配置上限之内。 */
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
