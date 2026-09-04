package com.claubloom.harness.server.stream;

import com.claubloom.harness.protocol.stream.TranscriptProgress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * 基于 Project Reactor Sinks 多播流的会话事件广播器实现。
 */
@Slf4j
@Component
public class DefaultSessionEventBroadcaster implements SessionEventBroadcaster {

    private final Map<String, Sinks.Many<TranscriptProgress>> sinks = new ConcurrentHashMap<>();

    @Override
    public Flux<TranscriptProgress> subscribe(String sessionId) {
        log.info("[SSE Broadcaster] Client subscribing to session: {}", sessionId);
        Sinks.Many<TranscriptProgress> sink = sinks.computeIfAbsent(sessionId, id -> {
            log.info("[SSE Broadcaster] Creating new multicast sink for session: {}", id);
            return Sinks.many().multicast().onBackpressureBuffer();
        });
        return sink.asFlux();
    }

    @Override
    public void publish(String sessionId, TranscriptProgress progress) {
        Sinks.Many<TranscriptProgress> sink = sinks.get(sessionId);
        if (sink == null) {
            log.warn("[SSE Broadcaster] Dropped event for session {} because no sink/subscriber exists yet. Event: {}",
                    sessionId, progress.getClass().getSimpleName());
            return;
        }
        Sinks.EmitResult result = sink.tryEmitNext(progress);
        if (result.isFailure()) {
            log.warn("[SSE Broadcaster] Failed to emit event to session {}: {} (result: {})",
                    sessionId, progress.getClass().getSimpleName(), result);
        } else {
            log.debug("[SSE Broadcaster] Successfully emitted event to session {}: {}",
                    sessionId, progress.getClass().getSimpleName());
        }
    }

    @Override
    public void complete(String sessionId, Throwable error) {
        log.info("[SSE Broadcaster] Completing session stream {}: error={}", sessionId, error != null ? error.getMessage() : "none");
        Sinks.Many<TranscriptProgress> sink = sinks.remove(sessionId);
        if (sink == null) return;
        if (error != null) {
            sink.tryEmitError(error);
        } else {
            sink.tryEmitComplete();
        }
    }
}
