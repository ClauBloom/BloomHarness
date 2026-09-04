package com.claubloom.harness.server.transport.websocket;

import com.claubloom.harness.server.connection.ByteConnection;
import java.util.concurrent.CompletableFuture;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Adapts a Spring WebSocketSession to the server's ByteConnection abstraction,
 * sending each frame payload as one binary WebSocket message.
 * Mirrors pi's transport ByteConnection implementations (e.g. transports/unix).
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
            // Delivery failure is handled by onClose.
        }
    }

    @Override
    public CompletableFuture<Void> close() {
        try {
            session.close();
        } catch (Exception ignored) {
            // Closing twice is safe.
        }
        return CompletableFuture.completedFuture(null);
    }
}
