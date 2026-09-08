package com.claubloom.harness.server.connection;

/**
 * 连接生命周期阶段枚举：等待 Hello → 握手中 → 就绪 → 关闭中 → 已关闭。
 */
public enum ConnectionStage {
    AWAITING_HELLO,
    HANDSHAKING,
    READY,
    CLOSING,
    CLOSED
}
