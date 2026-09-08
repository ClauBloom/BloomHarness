package com.claubloom.harness.server.connection;

import java.util.concurrent.CompletableFuture;

/**
 * 一条已建立的有序字节连接的抽象接口。
 * 提供发送、关闭与状态查询操作，由具体传输层（WebSocket 等）实现。
 */
public interface ByteConnection {

    boolean isClosed();

    CompletableFuture<Void> send(byte[] chunk);

    CompletableFuture<Void> close();
}
