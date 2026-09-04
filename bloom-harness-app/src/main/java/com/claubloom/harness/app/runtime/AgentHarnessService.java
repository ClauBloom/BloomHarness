package com.claubloom.harness.app.runtime;

import com.claubloom.harness.core.loop.AgentLoop;
import com.claubloom.harness.core.loop.LlmCaller;
import com.claubloom.harness.core.prompt.SystemPromptBuilder;
import com.claubloom.harness.core.tool.ToolRegistry;
import com.claubloom.harness.protocol.model.ModelMetadata;
import com.claubloom.harness.protocol.model.ModelRef;
import com.claubloom.harness.protocol.session.SessionMetadata;
import com.claubloom.harness.protocol.session.ThinkingLevel;
import com.claubloom.harness.server.errors.SessionNotFoundError;
import com.claubloom.harness.server.service.CreateSessionOptions;
import com.claubloom.harness.server.service.PiServerService;
import com.claubloom.harness.server.service.PiSessionRuntime;
import com.claubloom.harness.server.stream.SessionEventBroadcaster;
import com.claubloom.harness.storage.service.SessionStorageService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Durable-session service backed by SQLite storage and the AgentLoop runtime.
 * Mirrors pi's PiServerService boundary (listSessions/listModels/createSession/openSession).
 */
@Slf4j
@Component
public class AgentHarnessService implements PiServerService {

    private final SessionStorageService storage;
    private final AgentLoop agentLoop;
    private final LlmCaller llmCaller;
    private final SessionEventBroadcaster broadcaster;
    private final SystemPromptBuilder systemPromptBuilder;
    private final ToolRegistry toolRegistry;
    private final ModelRef defaultModel;
    private final Map<String, PiSessionRuntime> liveRuntimes = new ConcurrentHashMap<>();

    public AgentHarnessService(
            SessionStorageService storage,
            AgentLoop agentLoop,
            LlmCaller llmCaller,
            SessionEventBroadcaster broadcaster,
            SystemPromptBuilder systemPromptBuilder,
            ToolRegistry toolRegistry,
            com.claubloom.harness.ai.config.AiProperties aiProperties) {
        this.storage = storage;
        this.agentLoop = agentLoop;
        this.llmCaller = llmCaller;
        this.broadcaster = broadcaster;
        this.systemPromptBuilder = systemPromptBuilder;
        this.toolRegistry = toolRegistry;
        this.defaultModel = parseModelRef(aiProperties.getDefaultModel());
    }

    @Override
    public CompletableFuture<List<SessionMetadata>> listSessions() {
        return CompletableFuture.completedFuture(storage.listSessions());
    }

    @Override
    public CompletableFuture<List<ModelMetadata>> listModels() {
        return CompletableFuture.completedFuture(List.of(new ModelMetadata(
                defaultModel.provider(), defaultModel.id(), defaultModel.id(), "openai-chat",
                false, List.of("text"), 128_000, 8_192,
                com.claubloom.harness.protocol.model.ModelCost.zero(),
                List.of(ThinkingLevel.OFF), true)));
    }

    @Override
    public CompletableFuture<PiSessionRuntime> createSession(CreateSessionOptions options) {
        storage.createSession(options.id(), options.cwd(), null, null);
        return CompletableFuture.completedFuture(buildRuntime(options));
    }

    @Override
    public CompletableFuture<PiSessionRuntime> openSession(String sessionId) {
        var snapshotOpt = storage.getSnapshot(sessionId);
        if (snapshotOpt.isEmpty()) {
            return CompletableFuture.failedFuture(new SessionNotFoundError("Session not found: " + sessionId));
        }
        var snapshot = snapshotOpt.get();
        return CompletableFuture.completedFuture(buildRuntime(new CreateSessionOptions(
                sessionId, snapshot.cwd(), snapshot.name(), snapshot.model(), snapshot.thinkingLevel())));
    }

    private PiSessionRuntime buildRuntime(CreateSessionOptions options) {
        return liveRuntimes.compute(options.id(), (id, existing) -> {
            if (existing != null) {
                if (options.model() != null) existing.setModel(options.model());
                if (options.thinkingLevel() != null) existing.setThinking(options.thinkingLevel());
                return existing;
            }
            return new AgentSessionRuntime(
                    id,
                    options.cwd() != null ? options.cwd() : System.getProperty("user.dir"),
                    agentLoop,
                    llmCaller,
                    storage,
                    broadcaster,
                    systemPromptBuilder.buildSystemPrompt(
                            options.cwd() != null ? options.cwd() : System.getProperty("user.dir"),
                            null,
                            List.copyOf(toolRegistry.list()),
                            null),
                    List.copyOf(toolRegistry.list()),
                    options.model() != null ? options.model() : defaultModel,
                    options.thinkingLevel());
        });
    }

    private static ModelRef parseModelRef(String value) {
        if (value == null || !value.contains(":")) {
            return null;
        }
        int split = value.indexOf(':');
        return ModelRef.of(value.substring(0, split), value.substring(split + 1));
    }
}
