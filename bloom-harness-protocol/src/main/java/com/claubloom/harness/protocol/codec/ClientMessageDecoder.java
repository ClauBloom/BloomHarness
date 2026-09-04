package com.claubloom.harness.protocol.codec;

import com.claubloom.harness.protocol.envelope.ClientMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * 从字节流中增量解码带帧结构的客户端报文。
 * 严格对齐 pi-agent 的 ClientMessageDecoder 编解码规范。
 */
public final class ClientMessageDecoder {

    private boolean failed = false;
    private final FrameDecoder frames;
    private final int maxFrameLength;

    public ClientMessageDecoder() {
        this(FrameCodec.DEFAULT_MAX_FRAME_LENGTH);
    }

    public ClientMessageDecoder(int maxFrameLength) {
        this.maxFrameLength = FrameCodec.resolveMaxFrameLength(maxFrameLength);
        this.frames = new FrameDecoder(this.maxFrameLength);
    }

    public int getMaxFrameLength() {
        return maxFrameLength;
    }

    public List<ClientMessage> push(byte[] chunk) {
        if (failed) throw new ProtocolValidationError("client message decoder has failed");
        try {
            List<ClientMessage> messages = new ArrayList<>();
            for (byte[] frame : frames.push(chunk)) {
                messages.add(ProtocolMessageCodec.decodeClientMessage(frame, maxFrameLength));
            }
            return messages;
        } catch (RuntimeException error) {
            failed = true;
            frames.end();
            throw error;
        }
    }

    public void end() {
        frames.end();
    }
}
