package com.claubloom.harness.server.transport.websocket;

import com.claubloom.harness.server.PiServer;
import com.claubloom.harness.server.connection.ByteConnectionHandler;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

/**
 * 通过 /ws/agent 端点承载分帧二进制协议的 WebSocket 处理器。
 * 将 Spring WebSocket 的连接建立/数据接收/关闭/错误事件桥接到 PiServer。
 */
@Slf4j
@RequiredArgsConstructor
public class AgentWebSocketHandler extends BinaryWebSocketHandler {

    private final PiServer server;
    private final Map<WebSocketSession, ByteConnectionHandler> handlers = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketByteConnection connection = new WebSocketByteConnection(session, null);
        ByteConnectionHandler handler = server.accept(connection);
        handlers.put(session, handler);
        log.debug("WebSocket connection accepted: {}", session.getId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ByteConnectionHandler handler = handlers.get(session);
        if (handler == null) return;
        ByteBuffer payload = message.getPayload();
        byte[] chunk = new byte[payload.remaining()];
        payload.get(chunk);
        handler.onData(chunk);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        ByteConnectionHandler handler = handlers.remove(session);
        if (handler != null) {
            handler.onClose();
        }
        log.debug("WebSocket connection closed: {} ({})", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws IOException {
        ByteConnectionHandler handler = handlers.remove(session);
        if (handler != null) {
            handler.onError(exception);
        }
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }
}
