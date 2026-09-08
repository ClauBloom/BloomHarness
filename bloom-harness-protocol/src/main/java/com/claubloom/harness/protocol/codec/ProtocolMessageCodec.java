package com.claubloom.harness.protocol.codec;

import com.claubloom.harness.protocol.envelope.ClientMessage;
import com.claubloom.harness.protocol.envelope.ServerMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

/**
 * 将协议消息作为带长度前缀的 CBOR 负载进行校验与编解码。
 * 忠实移植自 pi 的 protocol/src/codec.ts(encodeClientMessage、encodeServerMessage、
 * ClientMessageDecoder、decodeServerMessage),使用 Jackson 的 CBOR 后端,
 * 使线上格式与 pi 的帧化 CBOR 传输保持一致。
 */
public final class ProtocolMessageCodec {

    private static final ObjectMapper CBOR = new ObjectMapper(new CBORFactory());

    private ProtocolMessageCodec() {
    }

    /** CBOR 编解码往返共用的 ObjectMapper(仅为测试暴露)。 */
    public static ObjectMapper cborMapper() {
        return CBOR;
    }

    /** 校验并编码一条完整的带长度前缀的客户端消息。 */
    public static byte[] encodeClientMessage(ClientMessage message, Integer maxFrameLength) {
        return encodeProtocolMessage(message, ClientMessage.class, "client", maxFrameLength);
    }

    /** 校验并编码一条完整的带长度前缀的服务器消息。 */
    public static byte[] encodeServerMessage(ServerMessage message, Integer maxFrameLength) {
        return encodeProtocolMessage(message, ServerMessage.class, "server", maxFrameLength);
    }

    /** 将单个完整的数据帧载荷解码为客户端消息对象。 */
    public static ClientMessage decodeClientMessage(byte[] payload, Integer maxFrameLength) {
        return decodeProtocolMessage(payload, ClientMessage.class, "client", maxFrameLength);
    }

    /** 将单个完整的数据帧载荷解码为服务端消息对象。 */
    public static ServerMessage decodeServerMessage(byte[] payload, Integer maxFrameLength) {
        return decodeProtocolMessage(payload, ServerMessage.class, "server", maxFrameLength);
    }

    private static <T> byte[] encodeProtocolMessage(
            T value, Class<T> type, String kind, Integer maxFrameLength) {
        int limit = FrameCodec.resolveMaxFrameLength(maxFrameLength);
        try {
            byte[] payload = CBOR.writeValueAsBytes(value);
            if (payload.length > limit) {
                throw new FrameError("Frame length " + payload.length + " exceeds configured limit of " + limit);
            }
            byte[] frame = FrameCodec.encodeFrame(payload);
            FrameCodec.assertCompleteFrame(frame, limit);
            return frame;
        } catch (ProtocolValidationError e) {
            throw e;
        } catch (Exception e) {
            throw new ProtocolValidationError(
                    "Unable to encode " + kind + " protocol message: " + boundedErrorMessage(e), e);
        }
    }

    private static <T> T decodeProtocolMessage(
            byte[] payload, Class<T> type, String kind, Integer maxFrameLength) {
        int limit = FrameCodec.resolveMaxFrameLength(maxFrameLength);
        if (payload.length > limit) {
            throw new FrameError("Frame length " + payload.length + " exceeds configured limit of " + limit);
        }
        try {
            return CBOR.readValue(payload, type);
        } catch (Exception e) {
            throw new ProtocolValidationError("Invalid " + kind + " protocol message", e);
        }
    }

    private static String boundedErrorMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null) return "Unknown codec error";
        return message.length() <= 500 ? message : message.substring(0, 497) + "...";
    }
}
