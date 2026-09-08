package com.claubloom.harness.server.service;

import com.claubloom.harness.protocol.model.ModelMetadata;
import com.claubloom.harness.protocol.session.SessionMetadata;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 会话持久化与运行时独占获取的服务边界接口。
 * 实现方负责会话的列表查询、模型列表、创建与打开操作。
 */
public interface PiServerService {

    CompletableFuture<List<SessionMetadata>> listSessions();

    CompletableFuture<List<ModelMetadata>> listModels();

    CompletableFuture<PiSessionRuntime> createSession(CreateSessionOptions options);

    CompletableFuture<PiSessionRuntime> openSession(String sessionId);
}
