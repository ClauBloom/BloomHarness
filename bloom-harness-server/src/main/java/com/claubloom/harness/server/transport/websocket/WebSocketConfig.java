package com.claubloom.harness.server.transport.websocket;

import com.claubloom.harness.server.PiServer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 注册 /ws/agent WebSocket 端点，使分帧二进制协议可通过 WebSocket 传输。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final PiServer server;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new AgentWebSocketHandler(server), "/ws/agent")
                .setAllowedOriginPatterns("*");
    }
}
