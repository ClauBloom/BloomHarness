package com.claubloom.harness.server.service;

import com.claubloom.harness.protocol.model.ModelMetadata;
import com.claubloom.harness.protocol.session.SessionMetadata;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 持久化会话与独占获取的运行时的服务边界。
 * 对齐 pi 在 packages/server/src/types.ts 中的 PiServerService 接口。
 */
public interface PiServerService {

    CompletableFuture<List<SessionMetadata>> listSessions();

    CompletableFuture<List<ModelMetadata>> listModels();

    CompletableFuture<PiSessionRuntime> createSession(CreateSessionOptions options);

    CompletableFuture<PiSessionRuntime> openSession(String sessionId);
}
