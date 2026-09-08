package com.claubloom.harness.server.transport.websocket;

import com.claubloom.harness.server.connection.ByteConnection;
import java.util.concurrent.CompletableFuture;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 将 Spring 的 WebSocketSession 适配为服务器的 ByteConnection 抽象，
 * 将每个帧的负载作为一条二进制 WebSocket 消息发送。
 * 对齐 pi 的传输层 ByteConnection 实现（如 transports/unix）。
 */
public class WebSocketByteConnection implements ByteConnection {

    private final WebSocketSession session;
    private final Integer maxFrameLength;

    public WebSocketByteConnection(WebSocketSession session, Integer maxFrameLength) {
        this.session = session;
        this.maxFrameLength = maxFrameLength;
    }

    @Override
    public boolean isClosed() {
        return !session.isOpen();
    }

    @Override
    public CompletableFuture<Void> send(byte[] chunk) {
        if (!session.isOpen()) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            synchronized (this) {
                session.sendMessage(new BinaryMessage(chunk));
            }
            return CompletableFuture.completedFuture(null);
        } catch (Exception error) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(error);
            return failed;
        }
    }

    /** 便捷的文本帧创建方法（用于 SSE 桥接诊断）。 */
    public void sendText(String text) {
        try {
            synchronized (this) {
                session.sendMessage(new TextMessage(text));
            }
        } catch (Exception ignored) {
            // 投递失败由 onClose 处理。
        }
    }

    @Override
    public CompletableFuture<Void> close() {
        try {
            session.close();
        } catch (Exception ignored) {
            // 重复关闭是安全的。
        }
        return CompletableFuture.completedFuture(null);
    }
}
