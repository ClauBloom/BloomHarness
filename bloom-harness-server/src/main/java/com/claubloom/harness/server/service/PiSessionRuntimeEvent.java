package com.claubloom.harness.server.service;

import com.claubloom.harness.protocol.stream.TranscriptProgress;

/**
 * 由实时会话运行时发出的事件密封接口。
 * 包含快照变更、进度通知与致命错误三种事件类型。
 */
public sealed interface PiSessionRuntimeEvent {

    record SnapshotEvent() implements PiSessionRuntimeEvent {
    }

    record ProgressEvent(TranscriptProgress progress) implements PiSessionRuntimeEvent {
    }

    record ErrorEvent(Throwable error) implements PiSessionRuntimeEvent {
    }
}
