package com.claubloom.harness.server.service;

import com.claubloom.harness.protocol.stream.TranscriptProgress;

/**
 * 由实时会话运行时发出的事件。
 * 对齐 pi 在 packages/server/src/types.ts 中的 PiSessionRuntimeEvent 联合类型。
 */
public sealed interface PiSessionRuntimeEvent {

    record SnapshotEvent() implements PiSessionRuntimeEvent {
    }

    record ProgressEvent(TranscriptProgress progress) implements PiSessionRuntimeEvent {
    }

    record ErrorEvent(Throwable error) implements PiSessionRuntimeEvent {
    }
}
