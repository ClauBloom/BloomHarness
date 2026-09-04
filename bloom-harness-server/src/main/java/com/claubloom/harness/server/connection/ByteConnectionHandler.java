package com.claubloom.harness.server.connection;

/**
 * Handler callbacks for an accepted connection.
 * Mirrors pi's ByteConnectionHandler in packages/server/src/connection.ts.
 */
public interface ByteConnectionHandler {

    void onData(byte[] chunk);

    void onClose();

    void onError(Throwable error);
}
