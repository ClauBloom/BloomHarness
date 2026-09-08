package com.claubloom.harness.server.connection;

import com.claubloom.harness.protocol.codec.ClientMessageDecoder;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import lombok.Setter;

/**
 * 单条连接的状态容器：追踪握手阶段、已附加的会话 ID 集合，
 * 并持有该连接的帧解码器与断开标记。
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

    /** 判断连接是否已进入不可恢复的终态（已断开或正在/已关闭）。 */
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
