package com.claubloom.harness.server.connection;

import com.claubloom.harness.protocol.codec.ClientMessageDecoder;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import lombok.Setter;

/**
 * 按连接划分的握手状态机。
 * 对齐 pi 在 packages/server/src/connection.ts 中的 ConnectionState。
 */
@Getter
public class ConnectionState {

    private final String id;
    private final ByteConnection connection;
    private final ClientMessageDecoder decoder;
    private final Set<String> sessionIds = ConcurrentHashMap.newKeySet();
    @Setter
    private volatile ConnectionStage stage = ConnectionStage.AWAITING_HELLO;
    private final AtomicBoolean disconnected = new AtomicBoolean(false);
    private final AtomicBoolean handshakeComplete = new AtomicBoolean(false);
    @Setter
    private volatile CompletableFuture<Void> handshake;

    public ConnectionState(String id, ByteConnection connection) {
        this.id = id;
        this.connection = connection;
        this.decoder = new ClientMessageDecoder();
    }

    /** 对齐 pi 的 isTerminalConnection。 */
    public boolean isTerminal() {
        return disconnected.get()
                || stage == ConnectionStage.CLOSING
                || stage == ConnectionStage.CLOSED;
    }

    public AtomicBoolean isDisconnected() {
        return disconnected;
    }

    public AtomicBoolean isHandshakeComplete() {
        return handshakeComplete;
    }

    public boolean markDisconnected() {
        return disconnected.compareAndSet(false, true);
    }

    public boolean markHandshakeComplete() {
        return handshakeComplete.compareAndSet(false, true);
    }
}
