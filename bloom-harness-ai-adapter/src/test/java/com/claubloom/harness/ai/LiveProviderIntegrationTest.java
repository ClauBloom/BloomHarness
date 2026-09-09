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
import com.claubloom.harness.protocol.content.TextContent;
import com.claubloom.harness.protocol.message.AssistantMessage;
import com.claubloom.harness.protocol.message.UserMessage;
import com.claubloom.harness.protocol.model.ModelRef;
import com.claubloom.harness.protocol.session.Usage;
import com.claubloom.harness.protocol.tool.ToolResult;
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实供应商活体集成测试（需人工授权运行）。
 * <p>
 * 通过环境变量 {@code BLOOM_LIVE_TEST=true} 启用；读取项目根目录的 bloom-providers.json
 * （用户授权的测试凭据），走完整的 ai-router-core 路由引擎 + Spring AI 桥接链路，
 * 验证 anthropic 协议转换、流式输出与用量统计。
 */
@EnabledIfEnvironmentVariable(named = "BLOOM_LIVE_TEST", matches = "true")
class LiveProviderIntegrationTest {

    private static final Path PROVIDERS_FILE =
            Path.of("..", "bloom-providers.json").toAbsolutePath().normalize();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SpringAiModelAdapter adapter;
    private ProviderConfig provider;

    @BeforeEach
    void setUp() throws Exception {
        assertThat(PROVIDERS_FILE).as("bloom-providers.json 存在").exists();
        List<ProviderConfig> providers = objectMapper.readValue(
                PROVIDERS_FILE.toFile(), new TypeReference<List<ProviderConfig>>() {
                });
        assertThat(providers).as("已配置供应商").isNotEmpty();
        provider = providers.stream()
                .filter(p -> p.baseUrl() != null && !p.baseUrl().isBlank() && !p.models().isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("无有效供应商配置"));
        System.out.println("[LIVE] provider=" + provider.providerId()
                + " protocol=" + provider.protocol() + " model=" + provider.models().get(0));

        ProviderRegistry registry = new ProviderRegistry();
        registry.register(provider);

        ReasoningContentCache reasoningCache = new ReasoningContentCache();
        OpenAIRequestConverter openAiReq = new OpenAIRequestConverter(reasoningCache);
        ProtocolRegistry protocolRegistry = new ProtocolRegistry(
                List.of(openAiReq, new AnthropicRequestConverter()),
                List.of(new OpenAIResponseConverter(), new AnthropicResponseConverter()),
                List.of(new OpenAIStreamConverter(), new AnthropicStreamConverter()));

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

    @Test
    @DisplayName("LIVE: 真实供应商流式调用（协议转换 + 思考流 + 用量）")
    void live_streamingCall_againstRealProvider() throws Exception {
        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        AgentContext context = AgentContext.builder()
                .sessionId("bloom-live-test")
                .cwd(".")
                .messages(new ArrayList<>(List.of(UserMessage.text("用一句话介绍你自己。"))))
                .build();
        AgentLoopConfig config = AgentLoopConfig.builder()
                .model(new ModelRef(provider.providerId(), provider.models().get(0)))
                .systemPrompt("你是 BloomHarness 测试助手，回答保持简短。")
                .build();

        AssistantMessage result = adapter.call(context, config, events::add).get(120, TimeUnit.SECONDS);

        System.out.println("[LIVE] status=" + result.status() + " stopReason=" + result.stopReason()
                + " responseModel=" + result.responseModel() + " usage=" + result.usage());
        result.content().forEach(c -> System.out.println("[LIVE] content: "
                + c.getClass().getSimpleName() + " = "
                + (c instanceof TextContent tc ? tc.text() : c)));

        assertThat(result.status()).as("调用状态").isEqualTo("complete");
        assertThat(result.content()).as("内容非空").isNotEmpty();
        assertThat(result.content()).anySatisfy(c -> {
            assertThat(c).isInstanceOf(TextContent.class);
            assertThat(((TextContent) c).text()).isNotBlank();
        });
        Usage usage = result.usage();
        assertThat(usage).isNotNull();
        assertThat(usage.totalTokens()).as("token 用量").isGreaterThan(0);

        List<MessageUpdateEvent> updates = events.stream()
                .filter(MessageUpdateEvent.class::isInstance)
                .map(MessageUpdateEvent.class::cast)
                .toList();
        assertThat(updates).as("流式文本事件已发射").anyMatch(e -> "text".equals(e.kind()));
    }

    @Test
    @DisplayName("LIVE: 真实供应商工具调用（schema 声明，引擎不执行工具）")
    void live_toolSchemaDeclared_notExecutedInternally() throws Exception {
        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        AgentContext context = AgentContext.builder()
                .sessionId("bloom-live-tool-test")
                .cwd(".")
                .messages(new ArrayList<>(List.of(UserMessage.text(
                        "请调用 read 工具查看 README.md 的第一行，然后把内容原样告诉我。"))))
                .build();
        AgentLoopConfig config = AgentLoopConfig.builder()
                .model(new ModelRef(provider.providerId(), provider.models().get(0)))
                .systemPrompt("你是 BloomHarness 测试助手。")
                .tools(List.of(new com.claubloom.harness.core.tool.ToolDefinition() {
                    @Override
                    public String name() {
                        return "read";
                    }

                    @Override
                    public String description() {
                        return "读取工作区内文件内容";
                    }

                    @Override
                    public Map<String, Object> parameterSchema() {
                        return Map.of("type", "object",
                                "properties", Map.of("path", Map.of("type", "string")),
                                "required", List.of("path"));
                    }

                    @Override
                    public CompletableFuture<ToolResult> execute(ToolContext context, Map<String, Object> arguments) {
                        throw new UnsupportedOperationException("工具不应被 ChatModel 内部执行");
                    }
                }))
                .build();

        AssistantMessage result = adapter.call(context, config, events::add).get(120, TimeUnit.SECONDS);

        System.out.println("[LIVE-TOOL] status=" + result.status() + " stopReason=" + result.stopReason());
        result.content().forEach(c -> System.out.println("[LIVE-TOOL] content: " + c));

        // 无论模型是否决定调用工具，链路都应完整走完且状态健康；
        // 若模型发起工具调用，工具会以 ToolCallContent 返回（由 AgentLoop 的 ToolExecutor 执行，而非引擎内部）
        assertThat(result.status()).as("调用状态").isEqualTo("complete");
        assertThat(result.content()).as("内容非空").isNotEmpty();
    }
}
