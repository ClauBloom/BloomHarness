package com.claubloom.harness.app.runtime;

import com.claubloom.harness.core.loop.AgentContext;
import com.claubloom.harness.core.loop.AgentEventSink;
import com.claubloom.harness.core.loop.AgentLoop;
import com.claubloom.harness.core.loop.AgentLoopConfig;
import com.claubloom.harness.core.loop.LlmCaller;
import com.claubloom.harness.protocol.message.AgentMessage;
import com.claubloom.harness.protocol.message.UserMessage;
import com.claubloom.harness.protocol.model.ModelRef;
import com.claubloom.harness.protocol.session.SessionPhase;
import com.claubloom.harness.protocol.session.SessionSnapshot;
import com.claubloom.harness.protocol.session.ThinkingLevel;
import com.claubloom.harness.server.errors.SessionBusyError;
import com.claubloom.harness.server.service.PiSessionRuntime;
import com.claubloom.harness.server.service.PiSessionRuntimeEvent;
import com.claubloom.harness.server.stream.SessionEventBroadcaster;
import com.claubloom.harness.storage.service.SessionStorageService;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于 AgentLoop 与 SQLite 存储构建的活动会话运行时。
 * 实现提示词执行、中途干预、任务中断、模型与思考预算切换，支持快照持久化与事件流广播。
 */
@Slf4j
public class AgentSessionRuntime implements PiSessionRuntime {

    private final String sessionId;
    private volatile String cwd;
    private final AgentLoop agentLoop;
    private final LlmCaller llmCaller;
    private final SessionStorageService storage;
    private final SessionEventBroadcaster broadcaster;
    private final String systemPrompt;
    private final List<com.claubloom.harness.core.tool.ToolDefinition> tools;
    private final List<Consumer<PiSessionRuntimeEvent>> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final AtomicReference<SessionPhase> phase = new AtomicReference<>(SessionPhase.IDLE);
    private final AtomicReference<ModelRef> model = new AtomicReference<>(null);
    private final AtomicReference<ThinkingLevel> thinkingLevel = new AtomicReference<>(ThinkingLevel.OFF);
    private final java.util.Queue<com.claubloom.harness.protocol.message.UserMessage> steerQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();

    public AgentSessionRuntime(
            String sessionId,
            String cwd,
            AgentLoop agentLoop,
            LlmCaller llmCaller,
            SessionStorageService storage,
            SessionEventBroadcaster broadcaster,
            String systemPrompt,
            List<com.claubloom.harness.core.tool.ToolDefinition> tools,
            ModelRef model,
            ThinkingLevel thinkingLevel) {
        this.sessionId = sessionId;
        this.cwd = cwd;
        this.agentLoop = agentLoop;
        this.llmCaller = llmCaller;
        this.storage = storage;
        this.broadcaster = broadcaster;
        this.systemPrompt = systemPrompt;
        this.tools = tools != null ? tools : List.of();
        if (model != null) this.model.set(model);
        if (thinkingLevel != null) this.thinkingLevel.set(thinkingLevel);
    }

    @Override
    public SessionSnapshot snapshot() {
        return storage.getSnapshot(sessionId)
                .map(stored -> mergeRuntime(stored))
                .orElseGet(() -> new SessionSnapshot(
                        sessionId, sessionId, cwd,
                        System.currentTimeMillis(), System.currentTimeMillis(),
                        phase.get(), model.get(), thinkingLevel.get(),
                        false, true, 0,
                        List.of(), List.of(), 0));
    }

    private SessionSnapshot mergeRuntime(SessionSnapshot stored) {
        return new SessionSnapshot(
                stored.id(), stored.name(), stored.cwd(),
                stored.createdAt(), stored.updatedAt(),
                phase.get(), model.get(), thinkingLevel.get(),
                stored.attached(), stored.locked(), stored.revision(),
                stored.transcript(), java.util.List.copyOf(steerQueue), steerQueue.size());
    }

    @Override
    public SessionPhase getPhase() {
        return phase.get();
    }

    @Override
    public void prompt(String text) {
        if (!phase.compareAndSet(SessionPhase.IDLE, SessionPhase.TURN)) {
            throw new SessionBusyError();
        }
        try {
            UserMessage userMessage = UserMessage.text(text);
            // Snapshot the prior transcript BEFORE appending the prompt, because
            // AgentLoop prepends prompts to the context itself (pi agent-loop.ts behavior).
            List<AgentMessage> priorTranscript = storage.getTranscript(sessionId);
            storage.appendEntry(sessionId, "user_message", userMessage);
            broadcaster.publish(sessionId, new com.claubloom.harness.protocol.stream.TranscriptProgress.ItemStarted(userMessage));

            SessionEventBridge bridge = new SessionEventBridge(
                    sessionId,
                    (id, type, message) -> storage.appendEntry(id, type, message),
                    broadcaster);
            AgentLoopConfig config = buildConfig(bridge);
            AgentContext context = new AgentContext(sessionId, cwd, priorTranscript);

            List<AgentMessage> produced = agentLoop
                    .runAgentLoop(List.of(userMessage), context, config, bridge, llmCaller)
                    .join();
            log.info("Session {} prompt finished with {} messages", sessionId, produced.size());
            bridge.completeStream();
        } catch (RuntimeException error) {
            bridgeFail(error);
            throw error;
        } finally {
            phase.set(SessionPhase.IDLE);
            emit(new PiSessionRuntimeEvent.SnapshotEvent());
        }
    }

    private void bridgeFail(RuntimeException error) {
        broadcaster.complete(sessionId, error);
    }

    private AgentLoopConfig buildConfig(AgentEventSink sink) {
        return AgentLoopConfig.builder()
                .model(model.get())
                .thinkingLevel(thinkingLevel.get())
                .systemPrompt(systemPrompt)
                .tools(tools)
                .getSteeringMessages(() -> {
                    if (steerQueue.isEmpty()) return List.of();
                    List<AgentMessage> drained = java.util.List.copyOf(steerQueue);
                    steerQueue.clear();
                    return drained;
                })
                .build();
    }

    @Override
    public void steer(String text) {
        if (phase.get() != SessionPhase.TURN) {
            throw new com.claubloom.harness.server.errors.SessionBusyError(
                    "There is no active prompt to steer");
        }
        steerQueue.add(UserMessage.text(text));
    }

    @Override
    public void abort() {
        steerQueue.clear();
        phase.set(SessionPhase.IDLE);
        emit(new PiSessionRuntimeEvent.SnapshotEvent());
    }

    @Override
    public void setModel(ModelRef newModel) {
        model.set(newModel);
        emit(new PiSessionRuntimeEvent.SnapshotEvent());
    }

    @Override
    public void setThinking(ThinkingLevel level) {
        thinkingLevel.set(level);
        emit(new PiSessionRuntimeEvent.SnapshotEvent());
    }

    @Override
    public void setCwd(String newCwd) {
        if (newCwd != null && !newCwd.isBlank()) {
            this.cwd = newCwd;
            storage.updateSessionCwd(sessionId, newCwd);
            emit(new PiSessionRuntimeEvent.SnapshotEvent());
            log.info("Session {} runtime cwd switched to: {}", sessionId, newCwd);
        }
    }

    @Override
    public Runnable subscribe(Consumer<PiSessionRuntimeEvent> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    public void dispose() {
        listeners.clear();
        broadcaster.complete(sessionId, null);
    }

    private void emit(PiSessionRuntimeEvent event) {
        for (Consumer<PiSessionRuntimeEvent> listener : listeners) {
            listener.accept(event);
        }
    }
}
