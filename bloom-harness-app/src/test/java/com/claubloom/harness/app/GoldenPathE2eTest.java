package com.claubloom.harness.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.claubloom.harness.ai.provider.ProviderConfig;
import com.claubloom.harness.ai.provider.ProviderRegistry;
import com.claubloom.harness.protocol.content.TextContent;
import com.claubloom.harness.protocol.content.ToolCallContent;
import com.claubloom.harness.protocol.message.AgentMessage;
import com.claubloom.harness.protocol.message.AssistantMessage;
import com.claubloom.harness.protocol.message.ToolResultMessage;
import com.claubloom.harness.protocol.model.ModelRef;
import com.claubloom.harness.protocol.session.SessionPhase;
import com.claubloom.harness.protocol.session.SessionSnapshot;
import com.claubloom.harness.protocol.session.ThinkingLevel;
import com.claubloom.harness.protocol.stream.TranscriptProgress;
import com.claubloom.harness.server.service.PiServerService;
import com.claubloom.harness.server.service.PiSessionRuntime;
import com.claubloom.harness.server.service.CreateSessionOptions;
import com.claubloom.harness.server.stream.SessionEventBroadcaster;
import com.claubloom.harness.storage.service.SessionStorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 阶段 4 端到端黄金路径测试(TC-P4-04)。
 * 通过 PiServer 运行时边界驱动真实提示词:AgentLoop 触发后,
 * 模型依次发起 read 与 edit 工具调用(在路径沙箱内执行),
 * 结果持久化到 SQLite,SSE 进度事件持续推送直至完成。
 */
@SpringBootTest(classes = BloomHarnessApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class GoldenPathE2eTest {

    @Autowired
    private PiServerService service;

    @Autowired
    private SessionStorageService storage;

    @Autowired
    private SessionEventBroadcaster broadcaster;

    @Autowired
    private ProviderRegistry providerRegistry;

    @TempDir
    Path workspace;

    private MockWebServer llmServer;

    @DynamicPropertySource
    static void registerStorageProperties(DynamicPropertyRegistry registry) {
        String dbPath = System.getProperty("java.io.tmpdir") + "/golden-path-e2e-" + System.nanoTime() + ".db";
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbPath);
        registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
        registry.add("bloom.storage.database-path", () -> dbPath);
        registry.add("bloom.storage.auto-initialize", () -> "true");
    }

    @BeforeEach
    void setUp(@TempDir Path workspace) throws Exception {
        this.workspace = workspace;
        Files.writeString(workspace.resolve("test.txt"), "hello world\n");
        llmServer = new MockWebServer();
        llmServer.start();
        providerRegistry.register(ProviderConfig.of(
                "openai", "openai", llmServer.url("/v1").toString(), "test-key", "openai"));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (llmServer != null) {
            llmServer.shutdown();
        }
    }

    private static MockResponse sseResponse(String eventPayloads) {
        return new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setHeader("Connection", "close")
                .setBody(eventPayloads);
    }

    private static String toolCallChunk(String id, String name, String argumentsJson) {
        return "data: {\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"created\":1,"
                + "\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{"
                + "\"index\":0,\"id\":\"" + id + "\",\"type\":\"function\","
                + "\"function\":{\"name\":\"" + name + "\",\"arguments\":"
                + jsonQuote(argumentsJson) + "}}]},\"finish_reason\":null}]}\n\n";
    }

    private static String contentChunk(String text) {
        return "data: {\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"created\":1,"
                + "\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{\"content\":"
                + jsonQuote(text) + "},\"finish_reason\":null}]}\n\n";
    }

    private static String finalChunk(String finishReason) {
        return "data: {\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"created\":1,"
                + "\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{},"
                + "\"finish_reason\":\"" + finishReason + "\"}]}\n\n";
    }

    private static String jsonQuote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    @Test
    @Timeout(60)
    @DisplayName("TC-P4-04: prompt drives read + edit tool loop with SQLite persistence and SSE stream")
    void goldenPathShouldReadAndEditFile() throws Exception {
        AtomicInteger llmCalls = new AtomicInteger();
        llmServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return switch (llmCalls.incrementAndGet()) {
                    // 第 1 轮:模型请求调用 Read(test.txt)
                    case 1 -> sseResponse(toolCallChunk("call-read", "read", "{\"path\":\"test.txt\"}")
                            + finalChunk("tool_calls") + "data: [DONE]\n\n");
                    // 第 2 轮:模型在查看文件内容后请求调用 Edit(test.txt)
                    case 2 -> sseResponse(toolCallChunk("call-edit", "edit",
                            "{\"path\":\"test.txt\",\"old_string\":\"hello world\",\"new_string\":\"hello BloomHarness\"}")
                            + finalChunk("tool_calls") + "data: [DONE]\n\n");
                    // 第 3 轮:模型以最终消息完成
                    default -> sseResponse(contentChunk("Done: the file now says hello BloomHarness.")
                            + finalChunk("stop") + "data: [DONE]\n\n");
                };
            }
        });

        // 1. 通过 PiServerService 边界创建会话
        PiSessionRuntime runtime = service.createSession(CreateSessionOptions.builder()
                .id("golden-path-001")
                .cwd(workspace.toAbsolutePath().toString())
                .model(ModelRef.of("openai", "gpt-4o"))
                .thinkingLevel(ThinkingLevel.OFF)
                .build()).join();

        // 2. 在提示词执行期间收集 SSE 进度事件
        List<TranscriptProgress> progressEvents = new CopyOnWriteArrayList<>();
        var subscription = broadcaster.subscribe("golden-path-001")
                .doOnNext(progressEvents::add)
                .subscribe();
        try {
            // 3. 提示词:驱动完整的 read -> edit -> 最终消息 循环
            runtime.prompt("请读取 test.txt 并将其内容改写");
        } finally {
            subscription.dispose();
        }

        // 4. 对话记录已持久化到 SQLite:user、assistant(read)、tool result、
        //    assistant(edit)、tool result、assistant(最终文本)
        List<AgentMessage> transcript = storage.getTranscript("golden-path-001");
        assertThat(transcript).hasSize(6);
        assertThat(transcript.get(0)).isInstanceOf(com.claubloom.harness.protocol.message.UserMessage.class);

        AssistantMessage readTurn = (AssistantMessage) transcript.get(1);
        ToolCallContent readCall = (ToolCallContent) readTurn.content().stream()
                .filter(c -> c instanceof ToolCallContent).findFirst().orElseThrow();
        assertThat(readCall.toolName()).isEqualTo("read");
        assertThat(readCall.toolCallId()).isEqualTo("call-read");

        ToolResultMessage readResult = (ToolResultMessage) transcript.get(2);
        assertThat(readResult.toolCallId()).isEqualTo("call-read");
        assertThat(readResult.isError()).isFalse();
        assertThat(((TextContent) readResult.content().get(0)).text()).contains("hello world");

        AssistantMessage editTurn = (AssistantMessage) transcript.get(3);
        ToolCallContent editCall = (ToolCallContent) editTurn.content().stream()
                .filter(c -> c instanceof ToolCallContent).findFirst().orElseThrow();
        assertThat(editCall.toolName()).isEqualTo("edit");
        assertThat(editCall.toolCallId()).isEqualTo("call-edit");

        ToolResultMessage editResult = (ToolResultMessage) transcript.get(4);
        assertThat(editResult.toolCallId()).isEqualTo("call-edit");
        assertThat(editResult.isError()).isFalse();

        AssistantMessage finalTurn = (AssistantMessage) transcript.get(5);
        assertThat(((TextContent) finalTurn.content().get(0)).text())
                .contains("hello BloomHarness");

        // 5. 文件确实已被沙箱内的 EditTool 重写
        assertThat(Files.readString(workspace.resolve("test.txt")))
                .isEqualTo("hello BloomHarness\n");

        // 6. SSE 事件流收到了完整的事件序列
        assertThat(progressEvents).isNotEmpty();
        assertThat(progressEvents.stream()
                .anyMatch(p -> p instanceof TranscriptProgress.AssistantDelta))
                .as("expected assistant delta events for the final message").isTrue();
        assertThat(progressEvents.stream()
                .anyMatch(p -> p instanceof TranscriptProgress.ItemFinished item
                        && item.item() instanceof ToolResultMessage))
                .as("expected tool result item_finished events").isTrue();

        // 7. 运行时阶段回到 idle,且快照包含完整的对话记录
        assertThat(runtime.getPhase()).isEqualTo(SessionPhase.IDLE);
        SessionSnapshot snapshot = runtime.snapshot();
        assertThat(snapshot.transcript()).hasSize(6);

        assertThat(llmCalls.get()).as("expected exactly 3 LLM calls (read, edit, final)").isEqualTo(3);
    }
}
