package com.claubloom.harness.server.connection;

/**
 * Mirrors pi's ConnectionStage union in packages/server/src/connection.ts.
 */
public enum ConnectionStage {
    AWAITING_HELLO,
    HANDSHAKING,
    READY,
    CLOSING,
    CLOSED
}
