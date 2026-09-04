package com.claubloom.harness.server.stream;

import com.claubloom.harness.protocol.stream.TranscriptProgress;
import reactor.core.publisher.Flux;

/**
 * Publishes per-session transcript progress to streaming consumers (SSE).
 * Mirrors pi's runtime progress events (session_progress) exposed through an
 * HTTP-friendly channel for TC-P4-03 streaming passthrough.
 */
public interface SessionEventBroadcaster {

    /** Subscribes to progress for one session; completes when the run ends. */
    Flux<TranscriptProgress> subscribe(String sessionId);

    /** Publishes one progress item for a session. */
    void publish(String sessionId, TranscriptProgress progress);

    /** 完成（并移除）会话的事件流，标记任务执行结束。 */
    void complete(String sessionId, Throwable error);
}
