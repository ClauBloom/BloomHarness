package com.claubloom.harness.server.stream;

import com.claubloom.harness.protocol.stream.TranscriptProgress;
import reactor.core.publisher.Flux;

/**
 * 会话对话记录进度的发布接口，用于向流式消费者（如 SSE）广播实时事件。
 */
public interface SessionEventBroadcaster {

    /** 订阅单个会话的进度；运行结束时完成。 */
    Flux<TranscriptProgress> subscribe(String sessionId);

    /** 为某个会话发布一条进度项。 */
    void publish(String sessionId, TranscriptProgress progress);

    /** 完成（并移除）会话的事件流，标记任务执行结束。 */
    void complete(String sessionId, Throwable error);
}
