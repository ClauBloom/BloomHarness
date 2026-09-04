package com.claubloom.harness.server.sessions;

import com.claubloom.harness.protocol.command.AbortCommand;
import com.claubloom.harness.protocol.command.AbortResult;
import com.claubloom.harness.protocol.command.AttachCommand;
import com.claubloom.harness.protocol.command.AttachResult;
import com.claubloom.harness.protocol.command.Command;
import com.claubloom.harness.protocol.command.CommandResult;
import com.claubloom.harness.protocol.command.CreateCommand;
import com.claubloom.harness.protocol.command.CreateResult;
import com.claubloom.harness.protocol.command.DetachCommand;
import com.claubloom.harness.protocol.command.DetachResult;
import com.claubloom.harness.protocol.command.ListCommand;
import com.claubloom.harness.protocol.command.ListResult;
import com.claubloom.harness.protocol.command.PromptCommand;
import com.claubloom.harness.protocol.command.PromptResult;
import com.claubloom.harness.protocol.command.SetModelCommand;
import com.claubloom.harness.protocol.command.SetModelResult;
import com.claubloom.harness.protocol.command.SetThinkingCommand;
import com.claubloom.harness.protocol.command.SetThinkingResult;
import com.claubloom.harness.protocol.command.SteerCommand;
import com.claubloom.harness.protocol.command.SteerResult;
import com.claubloom.harness.protocol.envelope.EventEnvelope;
import com.claubloom.harness.protocol.envelope.ServerEvent;
import com.claubloom.harness.protocol.model.ModelRef;
import com.claubloom.harness.protocol.session.SessionMetadata;
import com.claubloom.harness.protocol.session.SessionPhase;
import com.claubloom.harness.protocol.session.SessionSnapshot;
import com.claubloom.harness.protocol.session.ThinkingLevel;
import com.claubloom.harness.server.connection.ConnectionStage;
import com.claubloom.harness.server.connection.ConnectionState;
import com.claubloom.harness.server.errors.PiServerError;
import com.claubloom.harness.server.service.CreateSessionOptions;
import com.claubloom.harness.server.service.PiServerService;
import com.claubloom.harness.server.service.PiSessionRuntime;
import com.claubloom.harness.server.service.PiSessionRuntimeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Tracks acquired session runtimes, attached connections, and command execution.
 * Faithful port of pi's LiveSessionManager in packages/server/src/sessions.ts
 * (acquire loop, attach/detach lifecycle, snapshot broadcasts, idle disposal).
 */
@Slf4j
public class LiveSessionManager {

    private final PiServerService service;
    private final Supplier<Boolean> isClosing;
    private final Consumer<Throwable> reportError;
    private final Runnable broadcastServerSnapshot;
    private final BiConsumer<ConnectionState, EventEnvelope> sendMessageCallback;
    private final Executor asyncExecutor;

    private final Map<String, LiveSession> liveSessions = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<LiveSession>> openingSessions = new ConcurrentHashMap<>();

    private static class LiveSession {
        final String id;
        final PiSessionRuntime runtime;
        final List<ConnectionState> connections = java.util.Collections.synchronizedList(new ArrayList<>());
        volatile Runnable unsubscribe = () -> {};
        volatile int operationCount = 0;
        volatile boolean ready = false;
        volatile boolean terminal = false;
        volatile CompletableFuture<Void> disposing;

        LiveSession(String id, PiSessionRuntime runtime) {
            this.id = id;
            this.runtime = runtime;
        }
    }

    public LiveSessionManager(
            PiServerService service,
            Supplier<Boolean> isClosing,
            Runnable broadcastServerSnapshot,
            BiConsumer<ConnectionState, EventEnvelope> sendMessage,
            Consumer<Throwable> reportError) {
        this.service = service;
        this.isClosing = isClosing;
        this.broadcastServerSnapshot = broadcastServerSnapshot;
        this.sendMessageCallback = sendMessage;
        this.reportError = reportError;
        this.asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /** 针对活跃会话注册表执行单条客户端命令。 */
    public CompletableFuture<CommandResult> executeCommand(ConnectionState connection, Command command) {
        return CompletableFuture.supplyAsync(() -> {
            switch (command) {
                case ListCommand listCommand -> {
                    return listMetadata()
                            .thenApply(ListResult::new)
                            .join();
                }
                case CreateCommand createCommand -> {
                    String id = UUID.randomUUID().toString();
                    CreateSessionOptions options = new CreateSessionOptions(
                            id, createCommand.cwd(), createCommand.name(),
                            createCommand.model(), createCommand.thinkingLevel());
                    LiveSession live = acquire(id, () -> service.createSession(options)).join();
                    attach(connection, live);
                    SessionSnapshot session = forConnection(broadcastSnapshot(live).join(), connection);
                    broadcastServerSnapshot.run();
                    return new CreateResult(session);
                }
                case AttachCommand attachCommand -> {
                    LiveSession live = acquire(
                            attachCommand.sessionId(),
                            () -> service.openSession(attachCommand.sessionId())).join();
                    attach(connection, live);
                    SessionSnapshot session = forConnection(broadcastSnapshot(live).join(), connection);
                    broadcastServerSnapshot.run();
                    return new AttachResult(session);
                }
                case DetachCommand detachCommand -> {
                    LiveSession live = liveSessions.get(detachCommand.sessionId());
                    if (connection.getSessionIds().remove(detachCommand.sessionId())) {
                        if (live != null) {
                            live.connections.remove(connection);
                            if (!live.connections.isEmpty() && !live.terminal && live.disposing == null) {
                                broadcastSnapshot(live).join();
                            }
                            maybeDispose(live).join();
                        }
                        broadcastServerSnapshot.run();
                    }
                    return new DetachResult(detachCommand.sessionId());
                }
                case PromptCommand promptCommand -> {
                    LiveSession live = requireAttached(connection, promptCommand.sessionId());
                    SessionSnapshot session = runOperation(live, () -> live.runtime.prompt(promptCommand.text()))
                            .join();
                    return new PromptResult(session);
                }
                case SteerCommand steerCommand -> {
                    LiveSession live = requireAttached(connection, steerCommand.sessionId());
                    SessionSnapshot session = runOperation(live, () -> live.runtime.steer(steerCommand.text()))
                            .join();
                    return new SteerResult(session);
                }
                case AbortCommand abortCommand -> {
                    LiveSession live = requireAttached(connection, abortCommand.sessionId());
                    SessionSnapshot session = runOperation(live, live.runtime::abort).join();
                    return new AbortResult(session);
                }
                case SetModelCommand setModelCommand -> {
                    LiveSession live = requireAttached(connection, setModelCommand.sessionId());
                    ModelRef model = setModelCommand.model();
                    SessionSnapshot session =
                            runOperation(live, () -> live.runtime.setModel(model)).join();
                    return new SetModelResult(session);
                }
                case SetThinkingCommand setThinkingCommand -> {
                    LiveSession live = requireAttached(connection, setThinkingCommand.sessionId());
                    ThinkingLevel level = setThinkingCommand.thinkingLevel();
                    SessionSnapshot session =
                            runOperation(live, () -> live.runtime.setThinking(level)).join();
                    return new SetThinkingResult(session);
                }
            }
        }, asyncExecutor);
    }

    /** Removes a disconnected connection from every attached session, disposing idle ones. */
    public CompletableFuture<Void> disconnect(ConnectionState connection) {
        List<LiveSession> sessions = connection.getSessionIds().stream()
                .map(liveSessions::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        connection.getSessionIds().clear();
        sessions.forEach(live -> live.connections.remove(connection));
        CompletableFuture<Void> all = CompletableFuture.completedFuture(null);
        for (LiveSession live : sessions) {
            all = all.thenCompose(v -> maybeDispose(live)
                    .exceptionally(error -> {
                        reportError.accept(error);
                        return null;
                    }));
        }
        return all;
    }

    /** 查询存储中的会话元数据，并与内存中的最新活跃快照合并覆盖。 */
    public CompletableFuture<List<SessionMetadata>> listMetadata() {
        return service.listSessions().thenCompose(stored -> {
            List<CompletableFuture<SessionSnapshot>> liveSnapshots = liveSessions.values().stream()
                    .filter(live -> live.disposing == null)
                    .map(live -> normalizedSnapshot(live))
                    .toList();
            return CompletableFuture.allOf(liveSnapshots.toArray(CompletableFuture[]::new))
                    .thenApply(v -> {
                        Map<String, SessionSnapshot> liveById = new ConcurrentHashMap<>();
                        for (CompletableFuture<SessionSnapshot> future : liveSnapshots) {
                            SessionSnapshot snapshot = future.join();
                            liveById.put(snapshot.id(), snapshot);
                        }
                        List<SessionMetadata> metadata = new ArrayList<>();
                        for (SessionMetadata item : stored) {
                            SessionSnapshot snapshot = liveById.remove(item.id());
                            if (snapshot == null) {
                                metadata.add(item);
                            } else {
                                metadata.add(toMetadata(snapshot));
                            }
                        }
                        for (SessionSnapshot snapshot : liveById.values()) {
                            metadata.add(toMetadata(snapshot));
                        }
                        return metadata;
                    });
        });
    }

    /** 释放所有活跃会话；对齐 pi-agent 的 close() 规范。 */
    public CompletableFuture<Void> close() {
        CompletableFuture<Void> openingAll = CompletableFuture.completedFuture(null);
        for (CompletableFuture<LiveSession> opening : List.copyOf(openingSessions.values())) {
            openingAll = openingAll.whenComplete((r, error) -> {
                if (error != null) reportError.accept(error);
            });
        }
        List<LiveSession> sessions = List.copyOf(liveSessions.values());
        liveSessions.clear();
        CompletableFuture<Void> closingAll = openingAll.thenCompose(v -> {
            CompletableFuture<Void> all = CompletableFuture.completedFuture(null);
            for (LiveSession live : sessions) {
                all = all.thenCompose(x -> disposeSession(live));
            }
            return all;
        });
        return closingAll;
    }

    private CompletableFuture<Void> disposeSession(LiveSession live) {
        if (live.disposing != null) {
            return live.disposing;
        }
        live.unsubscribe.run();
        live.disposing = CompletableFuture.runAsync(() -> live.runtime.dispose(), asyncExecutor);
        return live.disposing;
    }

    private SessionMetadata toMetadata(SessionSnapshot snapshot) {
        return new SessionMetadata(
                snapshot.id(),
                snapshot.createdAt(),
                snapshot.updatedAt(),
                null,
                snapshot.name(),
                snapshot.cwd());
    }

    private CompletableFuture<SessionSnapshot> runOperation(LiveSession live, Runnable operation) {
        live.operationCount += 1;
        return CompletableFuture.runAsync(() -> {
            try {
                operation.run();
            } catch (RuntimeException error) {
                throw error;
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        }, asyncExecutor)
                .thenCompose(v -> broadcastSnapshot(live))
                .whenComplete((snapshot, error) -> {
                    live.operationCount -= 1;
                    scheduleMaybeDispose(live);
                });
    }

    private CompletableFuture<LiveSession> acquire(
            String id, Supplier<CompletableFuture<PiSessionRuntime>> acquireRuntime) {
        for (;;) {
            LiveSession existing = liveSessions.get(id);
            if (existing != null) {
                if (existing.terminal) {
                    throw new PiServerError(com.claubloom.harness.protocol.result.ProtocolErrorCode.SESSION_LOCKED,
                            "Session runtime is terminating: " + id);
                }
                if (existing.disposing != null) {
                    return existing.disposing.thenCompose(v -> acquire(id, acquireRuntime));
                }
                return CompletableFuture.completedFuture(existing);
            }
            CompletableFuture<LiveSession> opening = openingSessions.get(id);
            if (opening != null) {
                return opening;
            }
            CompletableFuture<LiveSession> pending = create(id, acquireRuntime);
            openingSessions.put(id, pending);
            return pending.whenComplete((r, error) -> {
                if (openingSessions.get(id) == pending) {
                    openingSessions.remove(id);
                }
            });
        }
    }

    private CompletableFuture<LiveSession> create(
            String id, Supplier<CompletableFuture<PiSessionRuntime>> acquireRuntime) {
        return acquireRuntime.get().thenCompose(runtime -> {
            if (isClosing.get()) {
                runtime.dispose();
                throw new IllegalStateException("PiServer closed while acquiring a session runtime");
            }
            LiveSession live = new LiveSession(id, runtime);
            try {
                SessionSnapshot snapshot = runtime.snapshot();
                if (!snapshot.id().equals(id)) {
                    throw new PiServerError(
                            com.claubloom.harness.protocol.result.ProtocolErrorCode.INVALID_REQUEST,
                            "Service returned session " + snapshot.id()
                                    + " for server-assigned session " + id);
                }
                live.unsubscribe = runtime.subscribe(event -> handleRuntimeEvent(live, event));
                liveSessions.put(id, live);
                live.ready = true;
                return CompletableFuture.completedFuture(live);
            } catch (Throwable error) {
                live.unsubscribe.run();
                try {
                    runtime.dispose();
                } catch (Throwable disposeError) {
                    reportError.accept(disposeError);
                }
                throw error;
            }
        });
    }

    private void handleRuntimeEvent(LiveSession live, PiSessionRuntimeEvent event) {
        if (event instanceof PiSessionRuntimeEvent.ErrorEvent errorEvent) {
            terminate(live, errorEvent.error())
                    .exceptionally(error -> {
                        reportError.accept(error);
                        return null;
                    });
            return;
        }
        if (event instanceof PiSessionRuntimeEvent.ProgressEvent progressEvent) {
            EventEnvelope envelope = new EventEnvelope(new ServerEvent.SessionProgressEvent(
                    live.id, progressEvent.progress()));
            for (ConnectionState connection : snapshotConnections(live)) {
                sendMessage(connection, envelope);
            }
        } else {
            broadcastSnapshot(live)
                    .exceptionally(error -> {
                        reportError.accept(error);
                        return null;
                    });
        }
        scheduleMaybeDispose(live);
    }

    private CompletableFuture<Void> terminate(LiveSession live, Throwable error) {
        if (live.terminal) {
            return CompletableFuture.completedFuture(null);
        }
        live.terminal = true;
        reportError.accept(error);
        live.unsubscribe.run();
        List<ConnectionState> connections = snapshotConnections(live);
        CompletableFuture<Void> all = CompletableFuture.completedFuture(null);
        for (ConnectionState connection : connections) {
            all = all.thenCompose(v -> disconnect(connection));
        }
        return all.thenCompose(v -> maybeDispose(live));
    }

    private CompletableFuture<SessionSnapshot> normalizedSnapshot(LiveSession live) {
        SessionSnapshot snapshot = live.runtime.snapshot();
        if (!snapshot.id().equals(live.id)) {
            throw new PiServerError(
                    com.claubloom.harness.protocol.result.ProtocolErrorCode.INVALID_REQUEST,
                    "Runtime session ID changed from " + live.id + " to " + snapshot.id());
        }
        return CompletableFuture.completedFuture(new SessionSnapshot(
                snapshot.id(),
                snapshot.name(),
                snapshot.cwd(),
                snapshot.createdAt(),
                snapshot.updatedAt(),
                live.runtime.getPhase(),
                snapshot.model(),
                snapshot.thinkingLevel(),
                !snapshotConnections(live).isEmpty(),
                true,
                snapshot.revision(),
                snapshot.transcript(),
                snapshot.queuedSteer(),
                snapshot.queuedSteerCount()));
    }

    private SessionSnapshot forConnection(SessionSnapshot snapshot, ConnectionState connection) {
        boolean attached = connection.getSessionIds().contains(snapshot.id());
        return new SessionSnapshot(
                snapshot.id(), snapshot.name(), snapshot.cwd(), snapshot.createdAt(), snapshot.updatedAt(),
                snapshot.phase(), snapshot.model(), snapshot.thinkingLevel(),
                attached, snapshot.locked(), snapshot.revision(),
                snapshot.transcript(), snapshot.queuedSteer(), snapshot.queuedSteerCount());
    }

    private CompletableFuture<SessionSnapshot> broadcastSnapshot(LiveSession live) {
        return normalizedSnapshot(live).thenApply(snapshot -> {
            EventEnvelope envelope = new EventEnvelope(new ServerEvent.SessionSnapshotEvent(snapshot));
            for (ConnectionState connection : snapshotConnections(live)) {
                sendMessage(connection, envelope);
            }
            return snapshot;
        });
    }

    private void sendMessage(ConnectionState connection, EventEnvelope envelope) {
        try {
            sendMessageCallback.accept(connection, envelope);
        } catch (Throwable error) {
            log.warn("Failed delivering event to connection {}: {}", connection.getId(), error.getMessage());
        }
    }

    private void attach(ConnectionState connection, LiveSession live) {
        if (connection.isDisconnected().get()
                || connection.getStage() != ConnectionStage.READY
                || connection.getConnection().isClosed()) {
            maybeDispose(live);
            throw new PiServerError(
                    com.claubloom.harness.protocol.result.ProtocolErrorCode.INVALID_REQUEST,
                    "Connection closed while attaching to a session");
        }
        connection.getSessionIds().add(live.id);
        live.connections.add(connection);
    }

    private LiveSession requireAttached(ConnectionState connection, String sessionId) {
        if (!connection.getSessionIds().contains(sessionId)) {
            throw new PiServerError(
                    com.claubloom.harness.protocol.result.ProtocolErrorCode.INVALID_REQUEST,
                    "Connection is not attached to session " + sessionId);
        }
        LiveSession live = liveSessions.get(sessionId);
        if (live == null || live.terminal || live.disposing != null) {
            throw new PiServerError(
                    com.claubloom.harness.protocol.result.ProtocolErrorCode.NOT_FOUND,
                    "Session is not live: " + sessionId);
        }
        return live;
    }

    private void scheduleMaybeDispose(LiveSession live) {
        CompletableFuture.runAsync(() -> {
            try {
                maybeDispose(live).join();
            } catch (Throwable error) {
                reportError.accept(error);
            }
        }, asyncExecutor);
    }

    private CompletableFuture<Void> maybeDispose(LiveSession live) {
        if (isClosing.get()
                || !live.ready
                || live.disposing != null
                || !live.connections.isEmpty()
                || live.operationCount > 0
                || (!live.terminal && live.runtime.getPhase() != SessionPhase.IDLE)) {
            return live.disposing != null
                    ? live.disposing
                    : CompletableFuture.completedFuture(null);
        }
        live.unsubscribe.run();
        live.disposing = CompletableFuture.runAsync(() -> {
            try {
                live.runtime.dispose();
            } finally {
                if (liveSessions.get(live.id) == live) {
                    liveSessions.remove(live.id);
                }
            }
        }, asyncExecutor);
        return live.disposing.thenRun(() -> {
            if (!isClosing.get()) {
                broadcastServerSnapshot.run();
            }
        });
    }

    private List<ConnectionState> snapshotConnections(LiveSession live) {
        synchronized (live.connections) {
            return List.copyOf(live.connections);
        }
    }

    public Executor getAsyncExecutor() {
        return asyncExecutor;
    }

    public ExecutorService executorService() {
        return (ExecutorService) asyncExecutor;
    }
}
