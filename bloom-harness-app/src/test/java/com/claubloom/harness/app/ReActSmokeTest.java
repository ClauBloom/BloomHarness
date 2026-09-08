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
import com.claubloom.harness.server.service.CreateSessionOptions;
import com.claubloom.harness.server.service.PiServerService;
import com.claubloom.harness.server.service.PiSessionRuntime;
import com.claubloom.harness.storage.service.SessionStorageService;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 验证完整 ReAct 自主循环的冒烟测试:
 * 1. LLM 请求携带 OpenAI 'tools' 结构。
 * 2. LLM 以对 'write' 的工具调用作为响应。
 * 3. ToolExecutor 在 /home/clau/codes/test/ 内执行 write 工具。
 * 4. HelloWorld.cpp 成功创建于磁盘上。
 * 5. 工具结果回填到 Agent 上下文,LLM 产出最终回答。
 */
@SpringBootTest(classes = BloomHarnessApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class ReActSmokeTest {

    private static final String TEST_DIR = "/home/clau/codes/test";

    @Autowired
    private PiServerService service;

    @Autowired
    private SessionStorageService storage;

    @Autowired
    private ProviderRegistry providerRegistry;

    private MockWebServer llmServer;

    @DynamicPropertySource
    static void registerStorageProperties(DynamicPropertyRegistry registry) {
        String dbPath = System.getProperty("java.io.tmpdir") + "/react-smoke-test-" + System.nanoTime() + ".db";
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbPath);
        registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
        registry.add("bloom.storage.database-path", () -> dbPath);
        registry.add("bloom.storage.auto-initialize", () -> "true");
    }

    @BeforeEach
    void setUp() throws Exception {
        // 确保测试目录存在,并清理之前的 HelloWorld.cpp
        File testDir = new File(TEST_DIR);
        if (!testDir.exists()) {
            testDir.mkdirs();
        }
        Files.deleteIfExists(Paths.get(TEST_DIR, "HelloWorld.cpp"));

        llmServer = new MockWebServer();
        llmServer.start();
        providerRegistry.register(ProviderConfig.of(
                "openai", "openai", llmServer.url("/v1").toString(), "test-react-key", "openai"));
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
        return "data: {\"id\":\"chatcmpl-react-1\",\"object\":\"chat.completion.chunk\",\"created\":1,"
                + "\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{"
                + "\"index\":0,\"id\":\"" + id + "\",\"type\":\"function\","
                + "\"function\":{\"name\":\"" + name + "\",\"arguments\":"
                + jsonQuote(argumentsJson) + "}}]},\"finish_reason\":null}]}\n\n";
    }

    private static String contentChunk(String text) {
        return "data: {\"id\":\"chatcmpl-react-1\",\"object\":\"chat.completion.chunk\",\"created\":1,"
                + "\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{\"content\":"
                + jsonQuote(text) + "},\"finish_reason\":null}]}\n\n";
    }

    private static String finalChunk(String finishReason) {
        return "data: {\"id\":\"chatcmpl-react-1\",\"object\":\"chat.completion.chunk\",\"created\":1,"
                + "\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{},"
                + "\"finish_reason\":\"" + finishReason + "\"}]}\n\n";
    }

    private static String jsonQuote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    @Test
    @Timeout(60)
    @DisplayName("ReAct Smoke Test: Agent autonomously invokes write tool to create /home/clau/codes/test/HelloWorld.cpp and finishes turn")
    void testReActAgentWritesHelloWorldCpp() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        String cppSource = """
                #include <iostream>

                int main() {
                    std::cout << "Hello, World!" << std::endl;
                    return 0;
                }
                """;

        llmServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                int turn = callCount.incrementAndGet();

                // 验证发送给 LLM 的请求中包含 'tools' 与 'messages'
                String requestBody = request.getBody().readUtf8();
                assertThat(requestBody).contains("\"tools\"");
                assertThat(requestBody).contains("\"name\":\"write\"");

                if (turn == 1) {
                    // 第 1 轮:模型发起 write 工具调用以写入 HelloWorld.cpp
                    String args = "{\"path\":\"HelloWorld.cpp\",\"content\":" + jsonQuote(cppSource) + "}";
                    return sseResponse(
                            toolCallChunk("call-write-001", "write", args)
                            + finalChunk("tool_calls")
                            + "data: [DONE]\n\n"
                    );
                } else {
                    // 第 2 轮:模型看到工具结果并确认完成
                    return sseResponse(
                            contentChunk("我已经成功为您在 /home/clau/codes/test/ 目录下写入了 HelloWorld.cpp！")
                            + finalChunk("stop")
                            + "data: [DONE]\n\n"
                    );
                }
            }
        });

        // 1. 以 CWD = /home/clau/codes/test/ 创建会话
        String sessionId = "react-smoke-" + System.currentTimeMillis();
        PiSessionRuntime runtime = service.createSession(CreateSessionOptions.builder()
                .id(sessionId)
                .name("ReAct Smoke Test Session")
                .cwd(TEST_DIR)
                .model(ModelRef.of("openai", "gpt-4o"))
                .thinkingLevel(ThinkingLevel.OFF)
                .build()).join();

        // 2. 触发提示词以启动 ReAct 循环
        runtime.prompt("请在当前目录下写入一个 HelloWorld.cpp 文件");

        // 3. 打印对话记录,以便诊断工具执行过程
        List<AgentMessage> transcript = storage.getTranscript(sessionId);
        System.out.println("=== Transcript Size: " + transcript.size() + " ===");
        for (int i = 0; i < transcript.size(); i++) {
            System.out.println("Message [" + i + "]: " + transcript.get(i));
        }

        // 4. 验证 HelloWorld.cpp 确实存在于磁盘上!
        Path targetFile = Paths.get(TEST_DIR, "HelloWorld.cpp");
        assertThat(Files.exists(targetFile))
                .as("HelloWorld.cpp should be created on disk by ToolExecutor in %s, tool result was: %s",
                        TEST_DIR, transcript.size() > 2 ? transcript.get(2) : "none")
                .isTrue();
        assertThat(transcript).hasSize(4);

        // 第 1 轮工具调用
        AssistantMessage callMsg = (AssistantMessage) transcript.get(1);
        ToolCallContent toolCall = (ToolCallContent) callMsg.content().stream()
                .filter(c -> c instanceof ToolCallContent)
                .findFirst()
                .orElseThrow();
        assertThat(toolCall.toolName()).isEqualTo("write");

        // 工具结果
        ToolResultMessage resultMsg = (ToolResultMessage) transcript.get(2);
        assertThat(resultMsg.toolCallId()).isEqualTo("call-write-001");
        assertThat(resultMsg.isError()).isFalse();
        assertThat(((TextContent) resultMsg.content().get(0)).text()).contains("Successfully wrote");

        // 第 2 轮最终消息
        AssistantMessage finalMsg = (AssistantMessage) transcript.get(3);
        assertThat(((TextContent) finalMsg.content().get(0)).text()).contains("HelloWorld.cpp");

        // 5. 验证运行时阶段已回到 IDLE
        assertThat(runtime.getPhase()).isEqualTo(SessionPhase.IDLE);
        assertThat(callCount.get()).isEqualTo(2);

        System.out.println("=== ReAct Smoke Test PASSED! File generated: " + targetFile.toAbsolutePath() + " ===");
    }
}
