package com.claubloom.harness.server.service;

import com.claubloom.harness.protocol.model.ModelMetadata;
import com.claubloom.harness.protocol.session.SessionMetadata;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service boundary for durable sessions and exclusively acquired runtimes.
 * Mirrors pi's PiServerService interface in packages/server/src/types.ts.
 */
public interface PiServerService {

    CompletableFuture<List<SessionMetadata>> listSessions();

    CompletableFuture<List<ModelMetadata>> listModels();

    CompletableFuture<PiSessionRuntime> createSession(CreateSessionOptions options);

    CompletableFuture<PiSessionRuntime> openSession(String sessionId);
}
