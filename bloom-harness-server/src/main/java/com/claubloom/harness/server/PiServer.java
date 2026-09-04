package com.claubloom.harness.server;

import com.claubloom.harness.protocol.codec.ClientMessageDecoder;
import com.claubloom.harness.protocol.codec.ProtocolMessageCodec;
import com.claubloom.harness.protocol.codec.ProtocolValidationError;
import com.claubloom.harness.protocol.envelope.ClientHello;
import com.claubloom.harness.protocol.envelope.ClientMessage;
import com.claubloom.harness.protocol.envelope.EventEnvelope;
import com.claubloom.harness.protocol.envelope.RequestEnvelope;
import com.claubloom.harness.protocol.envelope.ResponseEnvelope;
import com.claubloom.harness.protocol.envelope.ServerHello;
import com.claubloom.harness.protocol.envelope.ServerHelloError;
import com.claubloom.harness.protocol.envelope.ServerMessage;
import com.claubloom.harness.protocol.session.ServerSnapshot;
import com.claubloom.harness.server.connection.ByteConnection;
import com.claubloom.harness.server.connection.ByteConnectionHandler;
import com.claubloom.harness.server.connection.ConnectionStage;
import com.claubloom.harness.server.connection.ConnectionState;
import com.claubloom.harness.server.errors.InternalServerError;
import com.claubloom.harness.server.errors.PiServerError;
import com.claubloom.harness.server.errors.PiServerErrors;
import com.claubloom.harness.server.protocol.ProtocolVersion;
import com.claubloom.harness.server.sessions.LiveSessionManager;
import com.claubloom.harness.server.snapshots.ServerSnapshotPublisher;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * 核心协议服务端：负责有序字节连接上的握手协商、请求分发与事件广播。
 * 严格对齐 pi-agent 的 PiServer 实现。
 */
@Slf4j
public class PiServer {

    /** Mirrors pi's DEFAULT_HANDSHAKE_TIMEOUT_MS. */
    public static final long DEFAULT_HANDSHAKE_TIMEOUT_MS = 5_000;

    private final String serverId;
    private final int maxFrameLength;
    private final long handshakeTimeoutMs;
    private final Consumer<Throwable> onError;
    private final Set<ConnectionState> connections = ConcurrentHashMap.newKeySet();
    private final LiveSessionManager sessions;
    private final ServerSnapshotPublisher snapshots;
    private volatile ServerSnapshotPublisher snapshotsRef;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();
    private volatile boolean closing = false;
    private volatile boolean started = false;

    public PiServer(
            String serverId,
            Integer maxFrameLength,
            Long handshakeTimeoutMs,
            Consumer<Throwable> onError,
            com.claubloom.harness.server.service.PiServerService service) {
        this.serverId = serverId != null && !serverId.isBlank() ? serverId : UUID.randomUUID().toString();
        this.maxFrameLength = com.claubloom.harness.protocol.codec.FrameCodec.resolveMaxFrameLength(maxFrameLength);
        this.handshakeTimeoutMs = handshakeTimeoutMs != null ? handshakeTimeoutMs : DEFAULT_HANDSHAKE_TIMEOUT_MS;
        this.onError = onError != null ? onError : error -> {};
        this.sessions = new LiveSessionManager(
                service,
                () -> closing,
                this::broadcastServerSnapshotQuietly,
                (connection, message) -> sendMessage(connection, message),
                this::reportError);
        this.snapshots = new ServerSnapshotPublisher(
                this.serverId,
                () -> List.copyOf(connections),
                () -> closing,
                sessions::listMetadata,
                service::listModels,
                (connection, envelope) -> sendMessage(connection, envelope),
                this::reportError);
        this.snapshotsRef = snapshots;
    }

    private void broadcastServerSnapshotQuietly() {
        ServerSnapshotPublisher publisher = snapshotsRef;
        if (publisher != null) {
            publisher.broadcast();
        }
    }

    public ServerSnapshotPublisher getSnapshots() {
        return snapshots;
    }

    public LiveSessionManager getSessions() {
        return sessions;
    }

    public Set<ConnectionState> getConnections() {
        return connections;
    }

    /** 接收一个新的连接并返回其处理器。对齐 pi-agent 的 accept() 方法。 */
    public ByteConnectionHandler accept(ByteConnection connection) {
        if (closing) {
            closeConnectionQuietly(connection);
            return new ByteConnectionHandler() {
                @Override public void onData(byte[] chunk) {}
                @Override public void onClose() {}
                @Override public void onError(Throwable error) { reportError(error); }
            };
        }

        ConnectionState state = new ConnectionState(UUID.randomUUID().toString(), connection);
        connections.add(state);
        scheduleHandshakeTimeout(state);

        return new ByteConnectionHandler() {
            @Override
            public void onData(byte[] chunk) {
                receive(state, chunk);
            }

            @Override
            public void onClose() {
                transportClosed(state);
            }

            @Override
            public void onError(Throwable error) {
                reportError(error);
                closeConnectionQuietly(connection);
                disconnect(state);
            }
        };
    }

    /** 关闭协议服务器并释放所有活跃会话。 */
    public CompletableFuture<Void> close() {
        closing = true;
        List<ConnectionState> all = List.copyOf(connections);
        for (ConnectionState connection : all) {
            connection.setStage(ConnectionStage.CLOSING);
        }
        return sessions.close().whenComplete((v, error) -> {
            for (ConnectionState connection : all) {
                closeConnectionQuietly(connection.getConnection());
                disconnect(connection);
            }
            connections.clear();
            started = false;
            scheduler.shutdown();
        });
    }

    @PreDestroy
    public void shutdown() {
        close().join();
    }

    private void receive(ConnectionState state, byte[] chunk) {
        if (state.isTerminal()) return;
        List<ClientMessage> messages;
        try {
            messages = state.getDecoder().push(chunk);
        } catch (RuntimeException error) {
            failProtocol(state, toProtocolErrorCode(error), error.getMessage());
            return;
        }
        for (ClientMessage message : messages) {
            if (state.isTerminal()) return;
            dispatchMessage(state, message);
        }
    }

    private void dispatchMessage(ConnectionState state, ClientMessage message) {
        if (state.getStage() == ConnectionStage.AWAITING_HELLO) {
            if (!(message instanceof ClientHello hello)) {
                failProtocol(state, "invalid_request", "The first client message must be hello");
                return;
            }
            state.setStage(ConnectionStage.HANDSHAKING);
            CompletableFuture<Void> handshake = finishHandshake(state, hello)
                    .exceptionally(error -> {
                        failProtocol(state,
                                error instanceof PiServerError piError ? piError.getCode().getValue() : "internal_error",
                                error.getMessage());
                        return null;
                    });
            state.setHandshake(handshake);
            return;
        }

        if (message instanceof ClientHello) {
            failProtocol(state, "invalid_request", "hello may only be sent as the first message");
            return;
        }

        if (state.getStage() == ConnectionStage.READY) {
            handleRequest(state, (RequestEnvelope) message);
            return;
        }
        if (state.getStage() != ConnectionStage.HANDSHAKING) return;
        CompletableFuture<Void> handshake = state.getHandshake();
        if (handshake == null) return;
        handshake.thenRun(() -> {
            if (state.getStage() == ConnectionStage.READY && !state.isDisconnected().get()
                    && message instanceof RequestEnvelope envelope) {
                handleRequest(state, envelope);
            }
        });
    }

    private CompletableFuture<Void> finishHandshake(ConnectionState state, ClientHello hello) {
        if (!ProtocolVersion.isSupported(hello.version())) {
            failProtocol(state, "version",
                    "Unsupported protocol version " + hello.version()
                            + "; expected " + ProtocolVersion.CURRENT);
            return CompletableFuture.completedFuture(null);
        }

        return snapshots.get(null).thenCompose(snapshot -> {
            if (closing || state.isDisconnected().get()
                    || state.getStage() != ConnectionStage.HANDSHAKING
                    || state.getConnection().isClosed()) {
                return CompletableFuture.completedFuture(null);
            }
            boolean sent = sendMessage(state, new ServerHello(state.getId(), snapshot));
            if (sent && !state.isDisconnected().get() && state.getStage() == ConnectionStage.HANDSHAKING) {
                state.markHandshakeComplete();
                state.setStage(ConnectionStage.READY);
                CompletableFuture<Void> result = CompletableFuture.completedFuture(null);
                if (snapshot.revision() != snapshots.getCurrentRevision()) {
                    result = snapshots.get(null).thenCompose(current -> {
                        sendMessage(state, new EventEnvelope(new com.claubloom.harness.protocol.envelope.ServerEvent.ServerSnapshotEvent(current)));
                        return CompletableFuture.completedFuture(null);
                    });
                }
                return result;
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    private void handleRequest(ConnectionState state, RequestEnvelope envelope) {
        sessions.executeCommand(state, envelope.request())
                .whenComplete((result, error) -> {
                    if (error != null) {
                        Throwable unwrapped = unwrap(error);
                        if (unwrapped instanceof InternalServerError internal) {
                            reportError(internal.getCause());
                            sendMessage(state, ResponseEnvelope.failure(envelope.id(),
                                    PiServerErrors.toProtocolError(internal)));
                        } else {
                            sendMessage(state, ResponseEnvelope.failure(envelope.id(),
                                    PiServerErrors.toProtocolError(unwrapped)));
                        }
                        return;
                    }
                    sendMessage(state, ResponseEnvelope.success(envelope.id(), result));
                });
    }

    private void transportClosed(ConnectionState connection) {
        if (!connection.isDisconnected().get() && connection.getStage() != ConnectionStage.CLOSING) {
            try {
                connection.getDecoder().end();
            } catch (RuntimeException error) {
                reportError(error);
            }
        }
        disconnect(connection);
    }

    private void disconnect(ConnectionState connection) {
        if (!connection.markDisconnected()) return;
        boolean handshakeComplete = connection.isHandshakeComplete().get();
        connection.setStage(ConnectionStage.CLOSED);
        connections.remove(connection);
        sessions.disconnect(connection).thenRun(() -> {
            if (!closing && handshakeComplete) snapshots.broadcast();
        });
    }

    private boolean sendMessage(ConnectionState connection, ServerMessage message) {
        if (connection.isDisconnected().get() || connection.getConnection().isClosed()) return false;
        byte[] frame;
        try {
            frame = ProtocolMessageCodec.encodeServerMessage(message, maxFrameLength);
        } catch (RuntimeException error) {
            reportError(error);
            closeConnectionQuietly(connection.getConnection());
            disconnect(connection);
            return false;
        }
        try {
            connection.getConnection().send(frame).join();
            return true;
        } catch (Exception error) {
            reportError(error);
            closeConnectionQuietly(connection.getConnection());
            disconnect(connection);
            return false;
        }
    }

    private void failProtocol(ConnectionState connection, String code, String message) {
        if (connection.isDisconnected().get()
                || connection.getStage() == ConnectionStage.CLOSING
                || connection.getStage() == ConnectionStage.CLOSED) {
            return;
        }
        connection.setStage(ConnectionStage.CLOSING);
        ServerHelloError helloError = new ServerHelloError(
                PiServerErrors.createProtocolError(code, message));
        byte[] finalFrame = null;
        try {
            finalFrame = ProtocolMessageCodec.encodeServerMessage(helloError, maxFrameLength);
        } catch (RuntimeException encodeError) {
            reportError(encodeError);
        }
        byte[] payload = finalFrame;
        try {
            connection.getConnection().send(payload).join();
        } catch (Exception sendError) {
            reportError(sendError);
        }
        closeConnectionQuietly(connection.getConnection());
        disconnect(connection);
    }

    private void scheduleHandshakeTimeout(ConnectionState state) {
        scheduler.schedule(() -> {
            if (!state.isTerminal() && state.getStage() != ConnectionStage.READY) {
                failProtocol(state, "invalid_request", "Handshake timeout");
            }
        }, handshakeTimeoutMs, TimeUnit.MILLISECONDS);
    }

    private void closeConnectionQuietly(ByteConnection connection) {
        try {
            connection.close();
        } catch (RuntimeException error) {
            reportError(error);
        }
    }

    private String toProtocolErrorCode(Throwable error) {
        if (error instanceof com.claubloom.harness.protocol.codec.FrameError) {
            return "invalid_request";
        }
        if (error instanceof ProtocolValidationError) {
            return "invalid_request";
        }
        return "internal_error";
    }

    private Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while (cause != null && !(cause instanceof InternalServerError)
                && !(cause instanceof PiServerError)
                && cause.getCause() != null && cause.getCause() != cause) {
            if (cause instanceof java.util.concurrent.CompletionException
                    || cause instanceof java.util.concurrent.ExecutionException) {
                cause = cause.getCause();
                continue;
            }
            break;
        }
        return cause;
    }

    private void reportError(Throwable error) {
        try {
            onError.accept(error);
        } catch (RuntimeException ignored) {
            // Error observers cannot affect server state.
        }
    }

    /** 导出给必须运行自身接入循环的传输层使用。 */
    public boolean isStarted() {
        return started;
    }

    public void markStarted() {
        this.started = true;
    }
}
