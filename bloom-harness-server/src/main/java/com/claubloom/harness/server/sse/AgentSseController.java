package com.claubloom.harness.server.sse;

import com.claubloom.harness.protocol.stream.TranscriptProgress;
import com.claubloom.harness.server.stream.SessionEventBroadcaster;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Server-Sent Events (SSE) 端点，持续向客户端推送会话的对话记录进度事件。
 * 客户端监听 /api/stream/{sessionId} 接收实时流；
 * 事件以分片形式发送，任务结束时流自动完成。
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgentSseController {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SessionEventBroadcaster broadcaster;

    @GetMapping(value = "/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@PathVariable String sessionId) {
        log.info("[SSE Controller] Client opened SSE connection for session: {}", sessionId);
        return broadcaster.subscribe(sessionId)
                .doOnNext(progress -> log.info("[SSE Controller] Outgoing event to session {}: {}", sessionId, progress.getClass().getSimpleName()))
                .map(progress -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(toChunkJson(progress))
                        .build())
                .concatWith(Flux.defer(() -> {
                    log.info("[SSE Controller] Emitting stream END to session: {}", sessionId);
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("message")
                            .data("{\"type\":\"end\"}")
                            .build());
                }))
                .onErrorResume(error -> {
                    log.warn("SSE stream for session {} failed: {}", sessionId, error.getMessage());
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("message")
                            .data("{\"type\":\"error\",\"message\":" + jsonQuote(error.getMessage()) + "}")
                            .build());
                });
    }

    private static String toChunkJson(TranscriptProgress progress) {
        try {
            return JSON.writeValueAsString(java.util.Map.of("type", "chunk", "payload", progress));
        } catch (Exception error) {
            return "{\"type\":\"chunk\"}";
        }
    }

    private static String jsonQuote(String value) {
        if (value == null) return "null";
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception error) {
            return "\"" + value.replace("\"", "'") + "\"";
        }
    }
}
