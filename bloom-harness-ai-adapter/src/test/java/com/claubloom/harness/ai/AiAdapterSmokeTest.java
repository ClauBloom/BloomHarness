package com.claubloom.harness.ai;

import com.claubloom.harness.ai.adapter.SpringAiModelAdapter;
import com.claubloom.harness.ai.provider.ProviderConfig;
import com.claubloom.harness.ai.provider.ProviderRegistry;
import com.claubloom.harness.ai.router.RouterEngineConfiguration;
import com.claubloom.harness.core.event.AgentEvent;
import com.claubloom.harness.core.event.MessageUpdateEvent;
import com.claubloom.harness.core.loop.AgentContext;
import com.claubloom.harness.core.loop.AgentLoopConfig;
import com.claubloom.harness.core.tool.ToolContext;
import com.claubloom.harness.protocol.content.ImageContent;
import com.claubloom.harness.protocol.content.TextContent;
import com.claubloom.harness.protocol.content.ThinkingContent;
import com.claubloom.harness.protocol.content.ToolCallContent;
import com.claubloom.harness.protocol.message.AssistantMessage;
import com.claubloom.harness.protocol.message.ToolResultMessage;
import com.claubloom.harness.protocol.message.UserMessage;
import com.claubloom.harness.protocol.model.ModelRef;
import com.claubloom.harness.protocol.session.Usage;
import com.claubloom.harness.protocol.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniapi.router.core.api.RouterCore;
import com.miniapi.router.core.protocol.ProtocolRegistry;
import com.miniapi.router.core.protocol.ReasoningContentCache;
import com.miniapi.router.core.protocol.converter.anthropic.AnthropicRequestConverter;
import com.miniapi.router.core.protocol.converter.anthropic.AnthropicResponseConverter;
import com.miniapi.router.core.protocol.converter.anthropic.AnthropicStreamConverter;
import com.miniapi.router.core.protocol.converter.openai.OpenAIRequestConverter;
import com.miniapi.router.core.protocol.converter.openai.OpenAIResponseConverter;
import com.miniapi.router.core.protocol.converter.openai.OpenAIStreamConverter;
import com.miniapi.router.core.routing.RoutePipeline;
import com.miniapi.router.core.streaming.StreamProxy;
import com.miniapi.router.core.springai.ChatModelRouter;
import com.miniapi.router.core.spi.ApiKeyConfigRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 适配器冒烟测试（ai-router-core Spring AI 桥接层）。
 * <p>
 * 覆盖：协议消息 → Spring AI 消息映射；MockWebServer 端到端流式
 * （reasoning_content + 文本 + tool_calls delta）；错误诊断卡片映射；
 * 以及关键约束 —— ChatModel 内部不执行工具（ReAct 循环由 AgentLoop 掌控）。
 */
class AiAdapterSmokeTest {

    private MockWebServer upstream;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private SpringAiModelAdapter adapter;

    @AfterEach
    void stopUpstream() throws IOException {
        if (upstream != null) {
            upstream.shutdown();
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        upstream = new MockWebServer();
        upstream.start();
        ProviderRegistry registry = new ProviderRegistry();
        registry.register(new ProviderConfig("test", "Test Provider",
                upstream.url("/").toString(), "test-key", "openai", List.of("test-model")));

        ReasoningContentCache reasoningCache = new ReasoningContentCache();
        OpenAIRequestConverter openAiReq = new OpenAIRequestConverter(reasoningCache);
        OpenAIResponseConverter openAiResp = new OpenAIResponseConverter();
        OpenAIStreamConverter openAiStream = new OpenAIStreamConverter();
        AnthropicRequestConverter anthropicReq = new AnthropicRequestConverter();
        AnthropicResponseConverter anthropicResp = new AnthropicResponseConverter();
        AnthropicStreamConverter anthropicStream = new AnthropicStreamConverter();
        ProtocolRegistry protocolRegistry = new ProtocolRegistry(
                List.of(openAiReq, anthropicReq),
                List.of(openAiResp, anthropicResp),
                List.of(openAiStream, anthropicStream));

        RouterEngineConfiguration engine = new RouterEngineConfiguration();
        ApiKeyConfigRepository keyRepository = engine.bloomApiKeyConfigRepository(registry);
        var ruleRepository = engine.bloomRouteRuleRepository();
        var modelRepository = engine.bloomModelConfigRepository();
        var intentCatalog = engine.bloomIntentCatalogProvider();
        var upstreamClient = engine.upstreamClient();
        var cooldownTracker = engine.upstreamCooldownTracker();

        RoutePipeline routePipeline = engine.routePipeline(ruleRepository, keyRepository,
                engine.intentEvaluator(upstreamClient, engine.intentPromptTemplate(), intentCatalog, modelRepository),
                intentCatalog, engine.failureTracker(), engine.sessionRouteMemory(),
                modelRepository, engine.routeStrategyRegistry(), cooldownTracker);
        StreamProxy streamProxy = engine.streamProxy(upstreamClient, protocolRegistry,
                reasoningCache, engine.upstreamResponseParser(), cooldownTracker);
        RouterCore routerCore = engine.routerCore(routePipeline, streamProxy, protocolRegistry);

        var responseConverter = engine.springAiResponseConverter();
        ChatModelRouter chatModelRouter = engine.chatModelRouter(routerCore, protocolRegistry,
                engine.springAiPromptConverter(openAiReq), responseConverter,
                engine.springAiStreamConverter(responseConverter));

        adapter = new SpringAiModelAdapter(chatModelRouter, registry);
    }

    /**
     * TC-P2-01：协议消息应映射为 Spring AI 消息（系统提示词、多模态图片、工具调用与工具结果）。
     */
    @Test
    @DisplayName("TC-P2-01: Should map protocol messages to Spring AI messages with tools and media")
    void should_mapProtocolMessages_toSpringAiMessages() {
        AssistantMessage assistant = AssistantMessage.complete("a-1",
                List.of(new TextContent("我看一下"),
                        new ToolCallContent("call-1", "read", Map.of("path", "README.md"))),
                new ModelRef("test", "test-model"), "test-model", Usage.zero(),
                System.currentTimeMillis(), "toolUse");
        ToolResultMessage toolResult = new ToolResultMessage("t-1", "call-1", "read",
                Map.of("path", "README.md"), List.of(new TextContent("file content")),
                null, Usage.zero(), System.currentTimeMillis(), "complete", false);

        AgentContext context = AgentContext.builder().sessionId("s1").cwd(".")
                .messages(List.of(
                        new UserMessage("u-1",
                                List.of(new TextContent("看下这个图 "), new ImageContent("aGk=", "image/png")),
                                System.currentTimeMillis()),
                        assistant, toolResult))
                .build();
        AgentLoopConfig config = AgentLoopConfig.builder()
                .model(new ModelRef("test", "test-model"))
                .systemPrompt("你是测试助手")
                .build();

        var prompt = adapter.toSpringPrompt(context, config, new ModelRef("test", "test-model"));

        // SystemMessage + UserMessage + AssistantMessage + ToolResponseMessage
        assertThat(prompt.getInstructions()).hasSize(4);
        assertThat(prompt.getInstructions().get(0))
                .isInstanceOf(org.springframework.ai.chat.messages.SystemMessage.class);
        org.springframework.ai.chat.messages.UserMessage userMessage =
                (org.springframework.ai.chat.messages.UserMessage) prompt.getInstructions().get(1);
        assertThat(userMessage.getText()).isEqualTo("看下这个图 ");
        assertThat(userMessage.getMedia()).hasSize(1);
        org.springframework.ai.chat.messages.AssistantMessage springAssistant =
                (org.springframework.ai.chat.messages.AssistantMessage) prompt.getInstructions().get(2);
        assertThat(springAssistant.getToolCalls()).hasSize(1);
        assertThat(springAssistant.getToolCalls().get(0).id()).isEqualTo("call-1");
        assertThat(springAssistant.getToolCalls().get(0).name()).isEqualTo("read");
        org.springframework.ai.chat.messages.ToolResponseMessage toolResponse =
                (org.springframework.ai.chat.messages.ToolResponseMessage) prompt.getInstructions().get(3);
        assertThat(toolResponse.getResponses().get(0).id()).isEqualTo("call-1");
        assertThat(toolResponse.getResponses().get(0).responseData()).isEqualTo("file content");
    }

    /**
     * TC-P2-02：端到端流式（MockWebServer）—— reasoning_content 思考流、文本增量、
     * tool_calls 增量聚合，且 ChatModel 内部不执行工具（仅一次上游请求）。
     */
    @Test
    @DisplayName("TC-P2-02: Should stream reasoning/text/toolCalls and aggregate without internal tool execution")
    void should_streamReasoningTextToolCall_andAggregateWithoutInternalToolExecution() throws Exception {
        upstream.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(String.join("\n\n",
                        sse(chunk("{\"role\":\"assistant\",\"reasoning_content\":\"让我想想\"}", null, null)),
                        sse(chunk("{\"content\":\"Hello\"}", null, null)),
                        sse(chunk("{\"content\":\" world\"}", null, null)),
                        sse(chunk("{\"tool_calls\":[{\"index\":0,\"id\":\"call-1\",\"type\":\"function\","
                                + "\"function\":{\"name\":\"read\",\"arguments\":\"\"}}]}", "tool_calls", null)),
                        sse(chunk("{\"tool_calls\":[{\"index\":0,\"function\":"
                                + "{\"arguments\":\"{\\\"path\\\":\\\"README.md\\\"}\"}}]}", null, null)),
                        sse(chunk("{}", "tool_calls", null)),
                        "data: [DONE]")));

        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        AgentContext context = AgentContext.builder().sessionId("s-e2e").cwd(".")
                .messages(new ArrayList<>(List.of(UserMessage.text("read the readme"))))
                .build();
        AgentLoopConfig config = AgentLoopConfig.builder()
                .model(new ModelRef("test", "test-model"))
                .systemPrompt("你是测试助手")
                .tools(List.of(fakeTool("read")))
                .build();

        AssistantMessage result = adapter.call(context, config, events::add).get(60, TimeUnit.SECONDS);

        assertThat(result.status()).isEqualTo("complete");
        assertThat(result.stopReason()).isEqualTo("toolUse");
        assertThat(result.content()).anySatisfy(c -> {
            assertThat(c).isInstanceOf(ThinkingContent.class);
            assertThat(((ThinkingContent) c).thinking()).isEqualTo("让我想想");
        });
        assertThat(result.content()).anySatisfy(c -> {
            assertThat(c).isInstanceOf(TextContent.class);
            assertThat(((TextContent) c).text()).isEqualTo("Hello world");
        });
        assertThat(result.content()).anySatisfy(c -> {
            assertThat(c).isInstanceOf(ToolCallContent.class);
            ToolCallContent call = (ToolCallContent) c;
            assertThat(call.toolCallId()).isEqualTo("call-1");
            assertThat(call.toolName()).isEqualTo("read");
            assertThat(call.input()).isEqualTo(Map.of("path", "README.md"));
        });

        // 事件流：思考、文本与工具调用增量均已发出（index 约定 1=thinking / 2=toolCall）
        List<MessageUpdateEvent> updates = events.stream()
                .filter(MessageUpdateEvent.class::isInstance)
                .map(MessageUpdateEvent.class::cast)
                .toList();
        assertThat(updates).anyMatch(e -> "thinking".equals(e.kind()) && "让我想想".equals(e.delta()));
        assertThat(updates).anyMatch(e -> "text".equals(e.kind()) && "Hello".equals(e.delta()));
        assertThat(updates).anyMatch(e -> "text".equals(e.kind()) && " world".equals(e.delta()));
        assertThat(updates).anyMatch(e -> "toolCall".equals(e.kind()));

        // 关键约束：ChatModel 内部未执行工具 —— 上游仅收到一次请求（无工具结果回灌的第二轮）
        RecordedRequest recorded = upstream.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        String requestBody = recorded.getBody().readUtf8();
        assertThat(requestBody).contains("\"model\":\"test-model\"");
        assertThat(requestBody).contains("\"tools\"");
        assertThat(upstream.getRequestCount()).isEqualTo(1);
    }

    /**
     * TC-P2-03：上游 401 应映射为鉴权失败诊断卡片（BloomAiException），消息进入 error 状态。
     */
    @Test
    @DisplayName("TC-P2-03: Should map 401 upstream error to auth diagnostic card")
    void should_mapUnauthorizedUpstream_toDiagnosticCard() throws Exception {
        upstream.enqueue(new MockResponse().setResponseCode(401)
                .setBody("{\"error\":{\"message\":\"Incorrect API key provided\"}}"));

        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        AgentContext context = AgentContext.builder().sessionId("s-401").cwd(".")
                .messages(new ArrayList<>(List.of(UserMessage.text("hello"))))
                .build();
        AgentLoopConfig config = AgentLoopConfig.builder()
                .model(new ModelRef("test", "test-model"))
                .build();

        AssistantMessage result = adapter.call(context, config, events::add).get(30, TimeUnit.SECONDS);

        assertThat(result.status()).isEqualTo("error");
        assertThat(result.errorMessage()).contains("鉴权失败");
        assertThat(result.errorMessage()).contains("Incorrect API key provided");
        assertThat(events).anyMatch(e -> e instanceof MessageUpdateEvent update
                && update.delta().contains("鉴权失败"));
    }

    /**
     * TC-P2-04：路由失败（模型未在任何供应商声明）应映射为无可用上游诊断。
     */
    @Test
    @DisplayName("TC-P2-04: Should map NO_AVAILABLE_UPSTREAM to diagnostic card")
    void should_mapUnknownModel_toNoRouteDiagnosticCard() throws Exception {
        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        AgentContext context = AgentContext.builder().sessionId("s-404").cwd(".")
                .messages(new ArrayList<>(List.of(UserMessage.text("hello"))))
                .build();
        AgentLoopConfig config = AgentLoopConfig.builder()
                .model(new ModelRef("test", "model-not-declared-anywhere"))
                .build();
        // 引擎策略选中唯一 Key 后仍会向上游发起请求；显式断开让回退链快速失败
        upstream.enqueue(new MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START));

        AssistantMessage result = adapter.call(context, config, events::add).get(60, TimeUnit.SECONDS);

        assertThat(result.status()).isEqualTo("error");
        assertThat(result.errorMessage()).contains("无可用上游");
    }

    /* ---------- 辅助 ---------- */

    private static String sse(String json) {
        return "data: " + json;
    }

    /** 构建单个 OpenAI 流式 chunk：deltaObject 为完整的 delta JSON 对象字符串 */
    private static String chunk(String deltaObject, String finishReason, String role) {
        String finishJson = finishReason != null ? "\"finish_reason\":\"" + finishReason + "\"" : "\"finish_reason\":null";
        return "{\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"model\":\"test-model\","
                + "\"choices\":[{\"index\":0,\"delta\":" + deltaObject + "," + finishJson + "}]}";
    }

    /** 测试用工具定义：若被 ChatModel 内部执行将直接抛错，便于断言"未被执行" */
    private com.claubloom.harness.core.tool.ToolDefinition fakeTool(String name) {
        return new com.claubloom.harness.core.tool.ToolDefinition() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "test tool";
            }

            @Override
            public Map<String, Object> parameterSchema() {
                return Map.of("type", "object",
                        "properties", Map.of("path", Map.of("type", "string")));
            }

            @Override
            public CompletableFuture<ToolResult> execute(ToolContext context, Map<String, Object> arguments) {
                throw new UnsupportedOperationException("工具不应被 ChatModel 内部执行");
            }
        };
    }
}
