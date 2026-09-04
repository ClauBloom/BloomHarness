package com.claubloom.harness.server.snapshots;

import com.claubloom.harness.protocol.envelope.EventEnvelope;
import com.claubloom.harness.protocol.envelope.ServerEvent;
import com.claubloom.harness.protocol.model.ModelMetadata;
import com.claubloom.harness.protocol.session.ServerSnapshot;
import com.claubloom.harness.protocol.session.SessionMetadata;
import com.claubloom.harness.server.connection.ConnectionState;
import com.claubloom.harness.server.connection.ConnectionStage;
import com.claubloom.harness.server.service.PiServerService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes revision-stamped server snapshots to every ready connection, serializing broadcasts.
 * Faithful port of pi's ServerSnapshotPublisher in packages/server/src/snapshots.ts.
 */
@Slf4j
public class ServerSnapshotPublisher {

    private final String serverId;
    private final Supplier<List<ConnectionState>> connections;
    private final Supplier<Boolean> isClosing;
    private final Supplier<CompletableFuture<List<SessionMetadata>>> listSessions;
    private final Supplier<CompletableFuture<List<ModelMetadata>>> listModels;
    private final BiConsumer<ConnectionState, EventEnvelope> sendMessage;
    private final java.util.function.Consumer<Throwable> reportError;

    private final AtomicInteger revision = new AtomicInteger(0);
    private final AtomicReference<CompletableFuture<Void>> broadcastQueue =
            new AtomicReference<>(CompletableFuture.completedFuture(null));

    public ServerSnapshotPublisher(
            String serverId,
            Supplier<List<ConnectionState>> connections,
            Supplier<Boolean> isClosing,
            Supplier<CompletableFuture<List<SessionMetadata>>> listSessions,
            Supplier<CompletableFuture<List<ModelMetadata>>> listModels,
            BiConsumer<ConnectionState, EventEnvelope> sendMessage,
            java.util.function.Consumer<Throwable> reportError) {
        this.serverId = serverId;
        this.connections = connections;
        this.isClosing = isClosing;
        this.listSessions = listSessions;
        this.listModels = listModels;
        this.sendMessage = sendMessage;
        this.reportError = reportError;
    }

    public int getCurrentRevision() {
        return revision.get();
    }

    public CompletableFuture<ServerSnapshot> get(List<ModelMetadata> models) {
        return listSessions.get().thenCompose(sessions ->
                (models != null
                        ? CompletableFuture.completedFuture(models)
                        : listModels.get())
                        .thenApply(resolvedModels -> new ServerSnapshot(
                                serverId,
                                ServerSnapshot.PROTOCOL_VERSION,
                                revision.get(),
                                sessions,
                                resolvedModels)));
    }

    /** Queues a serialized broadcast; mirrors pi's promise-chained broadcast queue. */
    public CompletableFuture<Void> broadcast() {
        CompletableFuture<Void> broadcast = broadcastQueue.get()
                .thenCompose(v -> performBroadcast())
                .whenComplete((r, error) -> {
                    if (error != null) reportError.accept(error);
                });
        broadcastQueue.set(broadcast);
        return broadcast;
    }

    private CompletableFuture<Void> performBroadcast() {
        List<ConnectionState> readyConnections = connections.get().stream()
                .filter(c -> c.getStage() == ConnectionStage.READY && !c.isDisconnected().get())
                .toList();
        if (readyConnections.isEmpty() || isClosing.get()) {
            return CompletableFuture.completedFuture(null);
        }
        int nextRevision = revision.incrementAndGet();
        return listModels.get()
                .thenCompose(models -> get(models))
                .thenApply(snapshot -> new ServerSnapshot(
                        snapshot.serverId(),
                        snapshot.protocolVersion(),
                        nextRevision,
                        snapshot.sessions(),
                        snapshot.models()))
                .thenCompose(snapshot -> {
                    EventEnvelope envelope = new EventEnvelope(
                            new ServerEvent.ServerSnapshotEvent(snapshot));
                    CompletableFuture<Void> all = CompletableFuture.completedFuture(null);
                    for (ConnectionState connection : readyConnections) {
                        all = all.thenCompose(v ->
                                CompletableFuture.runAsync(() -> sendMessage.accept(connection, envelope)));
                    }
                    return all;
                });
    }
}
