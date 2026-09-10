package com.claubloom.harness.app.runtime;

import com.claubloom.harness.core.loop.AgentContext;
import com.claubloom.harness.core.loop.AgentEventSink;
import com.claubloom.harness.core.loop.AgentLoop;
import com.claubloom.harness.core.loop.AgentLoopConfig;
import com.claubloom.harness.core.loop.LlmCaller;
import com.claubloom.harness.core.tool.ToolDefinition;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final List<ToolDefinition> tools;
    private final com.claubloom.harness.core.config.CoreProperties coreProperties;
    private final List<Consumer<PiSessionRuntimeEvent>> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final AtomicReference<SessionPhase> phase = new AtomicReference<>(SessionPhase.IDLE);
    private final AtomicReference<ModelRef> model = new AtomicReference<>(null);
    private final AtomicReference<ThinkingLevel> thinkingLevel = new AtomicReference<>(ThinkingLevel.OFF);
    private final java.util.Queue<com.claubloom.harness.protocol.message.UserMessage> steerQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();

    /** 用户中止请求标志：由 {@link #abort()} 置位，循环在下一个边界退出 */
    private final AtomicBoolean abortRequested = new AtomicBoolean(false);
    /** 当前在途的 LLM 调用：中止时立即 cancel，中断流式输出、停止消耗上游 Token */
    private final AtomicReference<CompletableFuture<com.claubloom.harness.protocol.message.AssistantMessage>> activeCall =
            new AtomicReference<>(null);
    /** 传给 AgentLoop 的中止令牌 */
    private final com.claubloom.harness.core.loop.TurnAbortHandle abortHandle = new com.claubloom.harness.core.loop.TurnAbortHandle() {
        @Override
        public boolean isAborted() {
            return abortRequested.get();
        }

        @Override
        public void track(CompletableFuture<com.claubloom.harness.protocol.message.AssistantMessage> active) {
            activeCall.set(active);
        }
    };

    public AgentSessionRuntime(
            String sessionId,
            String cwd,
            AgentLoop agentLoop,
            LlmCaller llmCaller,
            SessionStorageService storage,
            SessionEventBroadcaster broadcaster,
            String systemPrompt,
            List<ToolDefinition> tools,
            ModelRef model,
            ThinkingLevel thinkingLevel) {
        this(sessionId, cwd, agentLoop, llmCaller, storage, broadcaster, systemPrompt, tools,
                model, thinkingLevel, new com.claubloom.harness.core.config.CoreProperties());
    }

    public AgentSessionRuntime(
            String sessionId,
            String cwd,
            AgentLoop agentLoop,
            LlmCaller llmCaller,
            SessionStorageService storage,
            SessionEventBroadcaster broadcaster,
            String systemPrompt,
            List<ToolDefinition> tools,
            ModelRef model,
            ThinkingLevel thinkingLevel,
            com.claubloom.harness.core.config.CoreProperties coreProperties) {
        this.sessionId = sessionId;
        this.cwd = cwd;
        this.agentLoop = agentLoop;
        this.llmCaller = llmCaller;
        this.storage = storage;
        this.broadcaster = broadcaster;
        this.systemPrompt = systemPrompt;
        this.tools = tools != null ? tools : List.of();
        this.coreProperties = coreProperties != null ? coreProperties : new com.claubloom.harness.core.config.CoreProperties();
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
        // 新轮次开始前复位中止状态，避免上一轮残留的中止请求误杀本次任务
        abortRequested.set(false);
        activeCall.set(null);
        try {
            UserMessage userMessage = UserMessage.text(text);
            // 在追加提示词之前,先对先前的对话记录做快照,因为
            // AgentLoop 会把提示词自行前置到上下文中。
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
                .maxTurns(coreProperties.getMaxTurns())
                .compactionThreshold(coreProperties.getCompactionThreshold())
                .abortHandle(abortHandle)
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
        // 请求协作式停止：循环在下一个边界（调用前/调用后/工具执行前）立即退出；
        // 同时取消在途 LLM 调用，中断流式输出并停止消耗上游 Token
        abortRequested.set(true);
        CompletableFuture<com.claubloom.harness.protocol.message.AssistantMessage> inFlight = activeCall.getAndSet(null);
        if (inFlight != null) {
            inFlight.cancel(true);
        }
        // 注意：不在此处把 phase 置回 IDLE——phase 由 prompt() 的 finally 在循环真正结束后归位。
        // 若提前置 IDLE，用户中止后立即发送新指令会绕过 SessionBusyError 校验，
        // 导致两个循环在同一会话上并发运行。
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
