package com.claubloom.harness.protocol.codec;

import com.claubloom.harness.protocol.envelope.ClientMessage;
import com.claubloom.harness.protocol.envelope.ServerMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

/**
 * Validates and encodes/decodes protocol messages as length-prefixed CBOR payloads.
 * Faithful port of pi protocol/src/codec.ts (encodeClientMessage, encodeServerMessage,
 * ClientMessageDecoder, decodeServerMessage) using Jackson's CBOR backend so the
 * wire format matches pi's framed CBOR transport.
 */
public final class ProtocolMessageCodec {

    private static final ObjectMapper CBOR = new ObjectMapper(new CBORFactory());

    private ProtocolMessageCodec() {
    }

    /** Object mapper shared for CBOR codec round-trips (exposed for testing only). */
    public static ObjectMapper cborMapper() {
        return CBOR;
    }

    /** Validates and encodes one complete length-prefixed client message. */
    public static byte[] encodeClientMessage(ClientMessage message, Integer maxFrameLength) {
        return encodeProtocolMessage(message, ClientMessage.class, "client", maxFrameLength);
    }

    /** Validates and encodes one complete length-prefixed server message. */
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
