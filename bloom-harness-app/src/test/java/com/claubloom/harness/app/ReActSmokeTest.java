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
 * Smoke test verifying the full ReAct Autonomous Loop:
 * 1. LLM request carries OpenAI 'tools' schema.
 * 2. LLM responds with a tool call to 'write'.
 * 3. ToolExecutor executes the write tool inside /home/clau/codes/test/.
 * 4. HelloWorld.cpp is successfully created on disk.
 * 5. Tool result is fed back into Agent context, LLM produces final answer.
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
        // Ensure test directory exists and clear previous HelloWorld.cpp
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

                // Verify request sent to LLM contains 'tools' and 'messages'
                String requestBody = request.getBody().readUtf8();
                assertThat(requestBody).contains("\"tools\"");
                assertThat(requestBody).contains("\"name\":\"write\"");

                if (turn == 1) {
                    // Turn 1: Model issues tool call to write HelloWorld.cpp
                    String args = "{\"path\":\"HelloWorld.cpp\",\"content\":" + jsonQuote(cppSource) + "}";
                    return sseResponse(
                            toolCallChunk("call-write-001", "write", args)
                            + finalChunk("tool_calls")
                            + "data: [DONE]\n\n"
                    );
                } else {
                    // Turn 2: Model sees tool result and confirms completion
                    return sseResponse(
                            contentChunk("我已经成功为您在 /home/clau/codes/test/ 目录下写入了 HelloWorld.cpp！")
                            + finalChunk("stop")
                            + "data: [DONE]\n\n"
                    );
                }
            }
        });

        // 1. Create session with CWD = /home/clau/codes/test/
        String sessionId = "react-smoke-" + System.currentTimeMillis();
        PiSessionRuntime runtime = service.createSession(CreateSessionOptions.builder()
                .id(sessionId)
                .name("ReAct Smoke Test Session")
                .cwd(TEST_DIR)
                .model(ModelRef.of("openai", "gpt-4o"))
                .thinkingLevel(ThinkingLevel.OFF)
                .build()).join();

        // 2. Trigger prompt to start ReAct loop
        runtime.prompt("请在当前目录下写入一个 HelloWorld.cpp 文件");

        // 3. Print transcript to diagnose tool execution
        List<AgentMessage> transcript = storage.getTranscript(sessionId);
        System.out.println("=== Transcript Size: " + transcript.size() + " ===");
        for (int i = 0; i < transcript.size(); i++) {
            System.out.println("Message [" + i + "]: " + transcript.get(i));
        }

        // 4. Verify file HelloWorld.cpp actually exists on disk!
        Path targetFile = Paths.get(TEST_DIR, "HelloWorld.cpp");
        assertThat(Files.exists(targetFile))
                .as("HelloWorld.cpp should be created on disk by ToolExecutor in %s, tool result was: %s",
                        TEST_DIR, transcript.size() > 2 ? transcript.get(2) : "none")
                .isTrue();
        assertThat(transcript).hasSize(4);

        // Turn 1 Tool Call
        AssistantMessage callMsg = (AssistantMessage) transcript.get(1);
        ToolCallContent toolCall = (ToolCallContent) callMsg.content().stream()
                .filter(c -> c instanceof ToolCallContent)
                .findFirst()
                .orElseThrow();
        assertThat(toolCall.toolName()).isEqualTo("write");

        // Tool Result
        ToolResultMessage resultMsg = (ToolResultMessage) transcript.get(2);
        assertThat(resultMsg.toolCallId()).isEqualTo("call-write-001");
        assertThat(resultMsg.isError()).isFalse();
        assertThat(((TextContent) resultMsg.content().get(0)).text()).contains("Successfully wrote");

        // Turn 2 Final message
        AssistantMessage finalMsg = (AssistantMessage) transcript.get(3);
        assertThat(((TextContent) finalMsg.content().get(0)).text()).contains("HelloWorld.cpp");

        // 5. Verify runtime phase returned to IDLE
        assertThat(runtime.getPhase()).isEqualTo(SessionPhase.IDLE);
        assertThat(callCount.get()).isEqualTo(2);

        System.out.println("=== ReAct Smoke Test PASSED! File generated: " + targetFile.toAbsolutePath() + " ===");
    }
}
