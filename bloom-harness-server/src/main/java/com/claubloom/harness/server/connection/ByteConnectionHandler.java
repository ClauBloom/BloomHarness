package com.claubloom.harness.server.connection;

/**
 * 已接受连接的回调处理器。
 * 对齐 pi 在 packages/server/src/connection.ts 中的 ByteConnectionHandler。
 */
public interface ByteConnectionHandler {

    void onData(byte[] chunk);

    void onClose();

    void onError(Throwable error);
}
