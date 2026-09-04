package com.claubloom.harness.protocol.codec;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Incrementally splits arbitrary byte chunks into length-prefixed payloads.
 * Faithful port of pi's FrameDecoder in protocol/src/framing.ts
 * (header accumulation, payload block buffering, open/ended/failed state machine).
 */
public final class FrameDecoder {

    private static final int PAYLOAD_BLOCK_SIZE = 64 * 1024;

    private enum DecoderState { OPEN, ENDED, FAILED }

    private final byte[] header = new byte[FrameCodec.FRAME_HEADER_LENGTH];
    private int headerLength = 0;
    private final int maxFrameLength;
    private final Deque<byte[]> payloadBlocks = new ArrayDeque<>();
    private byte[] currentPayloadBlock;
    private int currentPayloadBlockLength = 0;
    private int expectedPayloadLength = -1;
    private int payloadLength = 0;
    private DecoderState state = DecoderState.OPEN;

    public FrameDecoder() {
        this(FrameCodec.DEFAULT_MAX_FRAME_LENGTH);
    }

    public FrameDecoder(int maxFrameLength) {
        this.maxFrameLength = FrameCodec.resolveMaxFrameLength(maxFrameLength);
    }

    /** 输入一个数据块，并返回在本次推送过程中已接收完整的所有载荷。 */
    public List<byte[]> push(byte[] chunk) {
        if (state == DecoderState.ENDED) throw new FrameError("Frame decoder has ended");
        if (state == DecoderState.FAILED) throw new FrameError("Frame decoder has failed");
        if (chunk == null) throw new IllegalArgumentException("Frame chunk must not be null");

        List<byte[]> frames = new ArrayList<>();
        int chunkOffset = 0;
        while (chunkOffset < chunk.length) {
            if (expectedPayloadLength < 0) {
                int headerBytes = Math.min(
                        FrameCodec.FRAME_HEADER_LENGTH - headerLength,
                        chunk.length - chunkOffset);
                System.arraycopy(chunk, chunkOffset, header, headerLength, headerBytes);
                headerLength += headerBytes;
                chunkOffset += headerBytes;
                if (headerLength < FrameCodec.FRAME_HEADER_LENGTH) continue;

                long frameLength = FrameCodec.readLengthPrefix(header, 0);
                headerLength = 0;
                if (frameLength > maxFrameLength) {
                    fail("Frame length " + frameLength + " exceeds configured limit of " + maxFrameLength);
                }
                if (frameLength == 0) {
                    frames.add(new byte[0]);
                    continue;
                }
                expectedPayloadLength = (int) frameLength;
                payloadBlocks.clear();
                currentPayloadBlock = null;
                currentPayloadBlockLength = 0;
                payloadLength = 0;
            }

            while (chunkOffset < chunk.length && payloadLength < expectedPayloadLength) {
                byte[] block = currentPayloadBlock;
                if (block == null || currentPayloadBlockLength == block.length) {
                    block = new byte[Math.min(PAYLOAD_BLOCK_SIZE, expectedPayloadLength - payloadLength)];
                    payloadBlocks.addLast(block);
                    currentPayloadBlock = block;
                    currentPayloadBlockLength = 0;
                }
                int payloadBytes = Math.min(
                        block.length - currentPayloadBlockLength,
                        chunk.length - chunkOffset);
                System.arraycopy(chunk, chunkOffset, block, currentPayloadBlockLength, payloadBytes);
                currentPayloadBlockLength += payloadBytes;
                payloadLength += payloadBytes;
                chunkOffset += payloadBytes;
            }
            if (payloadLength == expectedPayloadLength) {
                frames.add(assemblePayload(expectedPayloadLength));
                payloadBlocks.clear();
                currentPayloadBlock = null;
                currentPayloadBlockLength = 0;
                expectedPayloadLength = -1;
                payloadLength = 0;
            }
        }
        return frames;
    }

    /** 标记流已传输完毕；若存在截断的尾随帧则解码器报错。 */
    public void end() {
        if (state == DecoderState.ENDED) throw new FrameError("Frame decoder has ended");
        if (state == DecoderState.FAILED) throw new FrameError("Frame decoder has failed");
        if (headerLength != 0 || expectedPayloadLength >= 0) {
            fail("Truncated frame at end of stream");
        }
        state = DecoderState.ENDED;
    }

    private byte[] assemblePayload(int expectedLength) {
        if (payloadBlocks.size() == 1) {
            byte[] only = payloadBlocks.peekFirst();
            if (only != null && only.length == expectedLength) return only;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(expectedLength);
        for (byte[] block : payloadBlocks) {
            out.write(block, 0, Math.min(block.length, expectedLength - out.size()));
        }
        return out.toByteArray();
    }

    private void fail(String message) {
        state = DecoderState.FAILED;
        headerLength = 0;
        payloadBlocks.clear();
        currentPayloadBlock = null;
        currentPayloadBlockLength = 0;
        expectedPayloadLength = -1;
        payloadLength = 0;
        throw new FrameError(message);
    }
}
