package com.claubloom.harness.server.connection;

/**
 * 对齐 pi 在 packages/server/src/connection.ts 中的 ConnectionStage 联合类型。
 */
public enum ConnectionStage {
    AWAITING_HELLO,
    HANDSHAKING,
    READY,
    CLOSING,
    CLOSED
}
