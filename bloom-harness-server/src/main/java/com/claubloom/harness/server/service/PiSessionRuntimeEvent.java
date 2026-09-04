package com.claubloom.harness.server.service;

import com.claubloom.harness.protocol.stream.TranscriptProgress;

/**
 * Events emitted by a live session runtime.
 * Mirrors pi's PiSessionRuntimeEvent union in packages/server/src/types.ts.
 */
public sealed interface PiSessionRuntimeEvent {

    record SnapshotEvent() implements PiSessionRuntimeEvent {
    }

    record ProgressEvent(TranscriptProgress progress) implements PiSessionRuntimeEvent {
    }

    record ErrorEvent(Throwable error) implements PiSessionRuntimeEvent {
    }
}
