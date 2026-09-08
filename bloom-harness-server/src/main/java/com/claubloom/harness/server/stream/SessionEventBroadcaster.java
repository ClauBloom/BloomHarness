package com.claubloom.harness.server.stream;

import com.claubloom.harness.protocol.stream.TranscriptProgress;
import reactor.core.publisher.Flux;

/**
 * 将会话的对话记录进度发布给流式消费者（SSE）。
 * 对齐 pi 的运行时进度事件（session_progress），通过面向 HTTP 的
 * 通道对外暴露，用于 TC-P4-03 流式透传。
 */
public interface SessionEventBroadcaster {

    /** 订阅单个会话的进度；运行结束时完成。 */
    Flux<TranscriptProgress> subscribe(String sessionId);

    /** 为某个会话发布一条进度项。 */
    void publish(String sessionId, TranscriptProgress progress);

    /** 完成（并移除）会话的事件流，标记任务执行结束。 */
    void complete(String sessionId, Throwable error);
}
