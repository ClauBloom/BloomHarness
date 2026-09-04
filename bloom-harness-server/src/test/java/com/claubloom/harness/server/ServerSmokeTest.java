package com.claubloom.harness.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.claubloom.harness.protocol.codec.FrameCodec;
import com.claubloom.harness.protocol.codec.ProtocolMessageCodec;
import com.claubloom.harness.protocol.command.Command;
import com.claubloom.harness.protocol.command.CommandResult;
import com.claubloom.harness.protocol.content.MessageContent;
import com.claubloom.harness.protocol.content.TextContent;
import com.claubloom.harness.protocol.envelope.ClientHello;
import com.claubloom.harness.protocol.envelope.ClientMessage;
import com.claubloom.harness.protocol.envelope.EventEnvelope;
import com.claubloom.harness.protocol.envelope.RequestEnvelope;
import com.claubloom.harness.protocol.envelope.ResponseEnvelope;
import com.claubloom.harness.protocol.envelope.ServerEvent;
import com.claubloom.harness.protocol.envelope.ServerHello;
import com.claubloom.harness.protocol.envelope.ServerHelloError;
import com.claubloom.harness.protocol.envelope.ServerMessage;
import com.claubloom.harness.protocol.model.ModelCost;
import com.claubloom.harness.protocol.model.ModelMetadata;
import com.claubloom.harness.protocol.model.ModelRef;
import com.claubloom.harness.protocol.message.AssistantMessage;
import com.claubloom.harness.protocol.message.UserMessage;
import com.claubloom.harness.protocol.session.SessionPhase;
import com.claubloom.harness.protocol.session.SessionSnapshot;
import com.claubloom.harness.protocol.session.ThinkingLevel;
import com.claubloom.harness.protocol.stream.TranscriptProgress;
import com.claubloom.harness.server.connection.ByteConnection;
import com.claubloom.harness.server.connection.ByteConnectionHandler;
import com.claubloom.harness.server.service.CreateSessionOptions;
import com.claubloom.harness.server.service.PiServerService;
import com.claubloom.harness.server.service.PiSessionRuntime;
import com.claubloom.harness.server.service.PiSessionRuntimeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Phase 4 server smoke test (TC-P4-02): framed pi protocol handshake, command
 * execution, and real-time event broadcasting over in-memory byte connections.
 * Mirrors pi's server testing suite (TestSessionRuntime semantics).
 */
public class ServerSmokeTest {

    private static final ModelMetadata TEST_MODEL = new ModelMetadata(
            "test", "small", "Test Small", "test-api", true,
            List.of("text", "image"), 16_000, 2_000, ModelCost.zero(),
            List.of(ThinkingLevel.OFF, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH), true);

    private PiServer server;
    private TestServerService service;
    private TestConnection clientConnection;
    private BlockingQueue<ServerMessage> received;

    @BeforeEach
    void setUp() {
        service = new TestServerService();
        server = new PiServer("test-server", null, null, error -> {}, service);
        received = new LinkedBlockingQueue<>();
        clientConnection = new TestConnection(received);
        ByteConnectionHandler handler = server.accept(clientConnection);
        clientConnection.setHandler(handler);
    }

    @AfterEach
    void tearDown() {
        server.close().join();
    }

    // ------------------------------------------------------------------
    // Protocol round-trip
    // ------------------------------------------------------------------

    private void sendClient(ClientMessage message) {
        clientConnection.getHandler().onData(ProtocolMessageCodec.encodeClientMessage(message, null));
    }

    private ServerMessage nextMessage() throws InterruptedException {
        ServerMessage message = received.poll(5, TimeUnit.SECONDS);
        assertThat(message).as("expected a server message within timeout").isNotNull();
        return message;
    }

    /** Pops messages until a response envelope for the given request id arrives. */
    private ResponseEnvelope nextResponse(String requestId, List<ServerMessage> sideBand) throws InterruptedException {
        for (;;) {
            ServerMessage message = nextMessage();
            if (message instanceof ResponseEnvelope response && response.id().equals(requestId)) {
                return response;
            }
            if (sideBand != null && message instanceof EventEnvelope envelope) {
                sideBand.add(message);
            }
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("TC-P4-02: handshake, create, prompt, and event broadcasting over framed connection")
    void shouldHandshakeCreateAndBroadcastEvents() throws Exception {
        // 1. Hello handshake -> ServerHello carrying initial server snapshot
        sendClient(new ClientHello(1));
        ServerMessage first = nextMessage();
        assertThat(first).isInstanceOf(ServerHello.class);
        ServerHello hello = (ServerHello) first;
        assertThat(hello.version()).isEqualTo(1);
        assertThat(hello.connectionId()).isNotBlank();
        assertThat(hello.snapshot().sessions()).isEmpty();
        assertThat(hello.snapshot().models()).hasSize(1);
        assertThat(hello.snapshot().serverId()).isEqualTo("test-server");

        // 2. create -> response + session_snapshot broadcast to attached connection
        List<ServerMessage> sideBand = new ArrayList<>();
        sendClient(new RequestEnvelope("req-1",
                new com.claubloom.harness.protocol.command.CreateCommand(
                        System.getProperty("user.dir"), "smoke", null, ThinkingLevel.OFF)));
        ResponseEnvelope response = nextResponse("req-1", sideBand);
        assertThat(response.ok()).isTrue();
        assertThat(response.result()).isInstanceOf(com.claubloom.harness.protocol.command.CreateResult.class);
        String sessionId = ((com.claubloom.harness.protocol.command.CreateResult) response.result())
                .session().id();

        // Broadcast triggered by create (session_snapshot and/or server_snapshot)
        List<ServerMessage> broadcasts = new ArrayList<>(sideBand);
        ServerMessage extra = received.poll(2, TimeUnit.SECONDS);
        if (extra != null) {
            broadcasts.add(extra);
        }
        assertThat(broadcasts.stream().filter(m -> m instanceof EventEnvelope)
                .map(m -> ((EventEnvelope) m).event())
                .anyMatch(e -> e instanceof ServerEvent.SessionSnapshotEvent))
                .as("expected a session_snapshot broadcast after create").isTrue();

        // 3. prompt -> runtime emits progress + snapshot events
        sideBand.clear();
        sendClient(new RequestEnvelope("req-2",
                new com.claubloom.harness.protocol.command.PromptCommand(sessionId, "hello world")));
        ResponseEnvelope promptResponse = nextResponse("req-2", sideBand);
        assertThat(promptResponse.ok()).isTrue();

        // Progress envelopes flow to the connection in real time
        List<ServerMessage> progressStream = new ArrayList<>(sideBand);
        for (int i = 0; i < 3; i++) {
            ServerMessage trailing = received.poll(1, TimeUnit.SECONDS);
            if (trailing == null) break;
            progressStream.add(trailing);
        }
        boolean sawProgress = false;
        boolean sawSnapshot = false;
        for (ServerMessage message : progressStream) {
            if (message instanceof EventEnvelope envelope) {
                if (envelope.event() instanceof ServerEvent.SessionProgressEvent progress
                        && progress.progress() instanceof TranscriptProgress.AssistantDelta delta) {
                    assertThat(delta.delta()).isEqualTo("reply:hello world");
                    sawProgress = true;
                }
                if (envelope.event() instanceof ServerEvent.SessionSnapshotEvent snapshotEvent) {
                    assertThat(snapshotEvent.snapshot().id()).isEqualTo(sessionId);
                    assertThat(snapshotEvent.snapshot().attached()).isTrue();
                    sawSnapshot = true;
                }
            }
        }
        assertThat(sawProgress).as("expected assistant_delta progress broadcast").isTrue();
        assertThat(sawSnapshot).as("expected session snapshot broadcast").isTrue();

        // 4. list reflects the live session
        sendClient(new RequestEnvelope("req-3", new com.claubloom.harness.protocol.command.ListCommand()));
        ResponseEnvelope listResponse = nextResponse("req-3", null);
        assertThat(listResponse.ok()).isTrue();
        CommandResult listResult = listResponse.result();
        assertThat(listResult).isInstanceOf(com.claubloom.harness.protocol.command.ListResult.class);
        assertThat(((com.claubloom.harness.protocol.command.ListResult) listResult).sessions())
                .hasSize(1);
        assertThat(((com.claubloom.harness.protocol.command.ListResult) listResult).sessions().get(0).id())
                .isEqualTo(sessionId);
    }

    @Test
    @Timeout(30)
    @DisplayName("protocol violations produce hello_error and close the connection")
    void shouldFailProtocolOnInvalidFirstMessage() throws Exception {
        // First message must be hello (pi server.ts dispatchMessage contract)
        sendClient(new RequestEnvelope("req-x",
                new com.claubloom.harness.protocol.command.ListCommand()));
        ServerMessage message = nextMessage();
        assertThat(message).isInstanceOf(ServerHelloError.class);
        assertThat(((ServerHelloError) message).error().code().getValue()).isEqualTo("invalid_request");
        assertThat(((ServerHelloError) message).error().message())
                .isEqualTo("The first client message must be hello");
    }

    @Test
    @Timeout(30)
    @DisplayName("unsupported protocol version is rejected with code 'version'")
    void shouldRejectUnsupportedVersion() throws Exception {
        sendClient(new ClientHello(999));
        ServerMessage message = nextMessage();
        assertThat(message).isInstanceOf(ServerHelloError.class);
        assertThat(((ServerHelloError) message).error().code().getValue()).isEqualTo("version");
    }

    @Test
    @Timeout(30)
    @DisplayName("abort and set_model commands return updated snapshots")
    void shouldAbortAndSetModel() throws Exception {
        sendClient(new ClientHello(1));
        assertThat(nextMessage()).isInstanceOf(ServerHello.class);

        sendClient(new RequestEnvelope("a-1",
                new com.claubloom.harness.protocol.command.CreateCommand(
                        System.getProperty("user.dir"), null, null, null)));
        ResponseEnvelope createResponse = nextResponse("a-1", null);
        assertThat(createResponse.ok()).isTrue();
        String sessionId = ((com.claubloom.harness.protocol.command.CreateResult) createResponse.result())
                .session().id();
        drainEvents();

        sendClient(new RequestEnvelope("a-2",
                new com.claubloom.harness.protocol.command.SetModelCommand(
                        sessionId, ModelRef.of("test", "large"))));
        ResponseEnvelope setModel = nextResponse("a-2", null);
        assertThat(setModel.ok()).isTrue();
        drainEvents();

        sendClient(new RequestEnvelope("a-3",
                new com.claubloom.harness.protocol.command.AbortCommand(sessionId)));
        ResponseEnvelope abortResponse = nextResponse("a-3", null);
        assertThat(abortResponse.ok()).isTrue();
        assertThat(((com.claubloom.harness.protocol.command.AbortResult) abortResponse.result())
                .session().phase()).isEqualTo(SessionPhase.IDLE);
    }

    /** Removes any pending event envelopes so assertions see responses only. */
    private void drainEvents() {
        received.removeIf(message -> message instanceof EventEnvelope);
    }

    // ------------------------------------------------------------------
    // Test doubles mirroring pi's server/src/testing
    // ------------------------------------------------------------------

    private static final class TestServerService implements PiServerService {

        private final Map<String, StoredSession> stored = new ConcurrentHashMap<>();

        @Override
        public CompletableFuture<List<com.claubloom.harness.protocol.session.SessionMetadata>> listSessions() {
            return CompletableFuture.completedFuture(stored.values().stream()
                    .map(s -> new com.claubloom.harness.protocol.session.SessionMetadata(
                            s.id, System.currentTimeMillis(), System.currentTimeMillis(),
                            null, s.name, s.cwd))
                    .toList());
        }

        @Override
        public CompletableFuture<List<ModelMetadata>> listModels() {
            return CompletableFuture.completedFuture(List.of(TEST_MODEL));
        }

        @Override
        public CompletableFuture<PiSessionRuntime> createSession(CreateSessionOptions options) {
            StoredSession session = new StoredSession(options.id(), options.cwd(), options.name());
            stored.put(options.id(), session);
            return CompletableFuture.completedFuture(new TestSessionRuntime(session));
        }

        @Override
        public CompletableFuture<PiSessionRuntime> openSession(String sessionId) {
            StoredSession session = stored.get(sessionId);
            if (session == null) {
                return CompletableFuture.failedFuture(new RuntimeException("Session not found"));
            }
            return CompletableFuture.completedFuture(new TestSessionRuntime(session));
        }
    }

    private static final class StoredSession {
        final String id;
        final String cwd;
        final String name;
        volatile SessionPhase phase = SessionPhase.IDLE;
        final List<com.claubloom.harness.protocol.message.AgentMessage> transcript =
                new CopyOnWriteArrayList<>();
        volatile ModelRef model = ModelRef.of("test", "small");
        volatile ThinkingLevel thinkingLevel = ThinkingLevel.OFF;
        final AtomicInteger revision = new AtomicInteger(0);

        StoredSession(String id, String cwd, String name) {
            this.id = id;
            this.cwd = cwd;
            this.name = name;
        }
    }

    private static final class TestSessionRuntime implements PiSessionRuntime {

        private final StoredSession stored;
        private final List<Consumer<PiSessionRuntimeEvent>> listeners = new CopyOnWriteArrayList<>();

        TestSessionRuntime(StoredSession stored) {
            this.stored = stored;
        }

        @Override
        public SessionSnapshot snapshot() {
            return new SessionSnapshot(
                    stored.id, stored.name, stored.cwd,
                    System.currentTimeMillis(), System.currentTimeMillis(),
                    stored.phase, stored.model, stored.thinkingLevel,
                    false, true, stored.revision.get(),
                    List.copyOf(stored.transcript), List.of(), 0);
        }

        @Override
        public SessionPhase getPhase() {
            return stored.phase;
        }

        @Override
        public void prompt(String text) {
            if (stored.phase != SessionPhase.IDLE) {
                throw new RuntimeException("Session is busy");
            }
            stored.phase = SessionPhase.TURN;
            UserMessage userMessage = UserMessage.text(text);
            stored.transcript.add(userMessage);
            stored.revision.incrementAndGet();
            emit(new PiSessionRuntimeEvent.ProgressEvent(new TranscriptProgress.ItemStarted(userMessage)));

            AssistantMessage assistant = AssistantMessage.complete(
                    UUID.randomUUID().toString(),
                    List.of(new TextContent("reply:" + text)),
                    stored.model, stored.model.provider() + "/" + stored.model.id(),
                    null, System.currentTimeMillis(), "stop");
            emit(new PiSessionRuntimeEvent.ProgressEvent(new TranscriptProgress.AssistantDelta(
                    assistant.id(), 0, "text", "reply:" + text)));
            stored.transcript.add(assistant);
            stored.revision.incrementAndGet();
            emit(new PiSessionRuntimeEvent.ProgressEvent(new TranscriptProgress.ItemFinished(assistant)));

            stored.phase = SessionPhase.IDLE;
            emit(new PiSessionRuntimeEvent.SnapshotEvent());
        }

        @Override
        public void steer(String text) {
            if (stored.phase == SessionPhase.IDLE) {
                throw new RuntimeException("There is no active prompt to steer");
            }
        }

        @Override
        public void abort() {
            stored.phase = SessionPhase.IDLE;
            emit(new PiSessionRuntimeEvent.SnapshotEvent());
        }

        @Override
        public void setModel(ModelRef model) {
            stored.model = model;
            emit(new PiSessionRuntimeEvent.SnapshotEvent());
        }

        @Override
        public void setThinking(ThinkingLevel thinkingLevel) {
            stored.thinkingLevel = thinkingLevel;
            emit(new PiSessionRuntimeEvent.SnapshotEvent());
        }

        @Override
        public Runnable subscribe(Consumer<PiSessionRuntimeEvent> listener) {
            listeners.add(listener);
            return () -> listeners.remove(listener);
        }

        @Override
        public void dispose() {
            listeners.clear();
        }

        private void emit(PiSessionRuntimeEvent event) {
            for (Consumer<PiSessionRuntimeEvent> listener : listeners) {
                listener.accept(event);
            }
        }
    }

    private static final class TestConnection implements ByteConnection {

        private final BlockingQueue<ServerMessage> received;
        private final FrameBuffer buffer = new FrameBuffer();
        private volatile ByteConnectionHandler handler;
        private volatile boolean closed = false;

        TestConnection(BlockingQueue<ServerMessage> received) {
            this.received = received;
        }

        ByteConnectionHandler getHandler() {
            return handler;
        }

        void setHandler(ByteConnectionHandler handler) {
            this.handler = handler;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public CompletableFuture<Void> send(byte[] chunk) {
            if (!closed) {
                buffer.push(chunk);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> close() {
            closed = true;
            return CompletableFuture.completedFuture(null);
        }

        /** Accumulates outgoing bytes and dispatches whole frames as decoded ServerMessages. */
        private final class FrameBuffer {
            private final List<byte[]> bytes = new ArrayList<>();

            synchronized void push(byte[] chunk) {
                bytes.add(chunk);
                int total = bytes.stream().mapToInt(b -> b.length).sum();
                byte[] combined = new byte[total];
                int offset = 0;
                for (byte[] part : bytes) {
                    System.arraycopy(part, 0, combined, offset, part.length);
                    offset += part.length;
                }
                int consumed = 0;
                while (consumed + FrameCodec.FRAME_HEADER_LENGTH <= total) {
                    long length = ((combined[consumed] & 0xffL) << 24)
                            | ((combined[consumed + 1] & 0xffL) << 16)
                            | ((combined[consumed + 2] & 0xffL) << 8)
                            | (combined[consumed + 3] & 0xffL);
                    if (consumed + FrameCodec.FRAME_HEADER_LENGTH + length > total) break;
                    byte[] payload = new byte[(int) length];
                    System.arraycopy(combined, consumed + FrameCodec.FRAME_HEADER_LENGTH, payload, 0, (int) length);
                    received.add(ProtocolMessageCodec.decodeServerMessage(payload, null));
                    consumed += FrameCodec.FRAME_HEADER_LENGTH + (int) length;
                }
                bytes.clear();
                if (consumed < total) {
                    byte[] rest = new byte[total - consumed];
                    System.arraycopy(combined, consumed, rest, 0, rest.length);
                    bytes.add(rest);
                }
            }
        }
    }
}
