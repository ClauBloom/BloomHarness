package com.claubloom.harness.server.connection;

import java.util.concurrent.CompletableFuture;

/**
 * An established, authorized ordered byte connection.
 * Mirrors pi's ByteConnection interface in packages/server/src/connection.ts.
 */
public interface ByteConnection {

    boolean isClosed();

    CompletableFuture<Void> send(byte[] chunk);

    CompletableFuture<Void> close();
}
