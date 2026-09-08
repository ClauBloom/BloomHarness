package com.claubloom.harness.server.connection;

import java.util.concurrent.CompletableFuture;

/**
 * 一条已建立、已授权的有序字节连接。
 * 对齐 pi 在 packages/server/src/connection.ts 中的 ByteConnection 接口。
 */
public interface ByteConnection {

    boolean isClosed();

    CompletableFuture<Void> send(byte[] chunk);

    CompletableFuture<Void> close();
}
