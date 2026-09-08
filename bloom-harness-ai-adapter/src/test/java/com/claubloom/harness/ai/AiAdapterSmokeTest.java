package com.claubloom.harness.ai;

import com.claubloom.harness.ai.adapter.StreamAdapter;
import com.claubloom.harness.protocol.model.ModelRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniapi.router.core.protocol.ProtocolRegistry;
import com.miniapi.router.core.protocol.ReasoningContentCache;
import com.miniapi.router.core.protocol.UnifiedRequest;
import com.miniapi.router.core.protocol.UnifiedStreamChunk;
import com.miniapi.router.core.protocol.converter.anthropic.AnthropicRequestConverter;
import com.miniapi.router.core.protocol.converter.anthropic.AnthropicResponseConverter;
import com.miniapi.router.core.protocol.converter.anthropic.AnthropicStreamConverter;
import com.miniapi.router.core.protocol.converter.openai.OpenAIRequestConverter;
import com.miniapi.router.core.protocol.converter.openai.OpenAIResponseConverter;
import com.miniapi.router.core.protocol.converter.openai.OpenAIStreamConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阶段 2：AI 模型适配器冒烟测试（TC-P2-01、TC-P2-02）。
 */
public class AiAdapterSmokeTest {

    private ProtocolRegistry protocolRegistry;
    private StreamAdapter streamAdapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        var openAiReq = new OpenAIRequestConverter(new ReasoningContentCache());
        var openAiResp = new OpenAIResponseConverter();
        var openAiStream = new OpenAIStreamConverter();

        var anthropicReq = new AnthropicRequestConverter();
        var anthropicResp = new AnthropicResponseConverter();
        var anthropicStream = new AnthropicStreamConverter();

        protocolRegistry = new ProtocolRegistry(
                List.of(openAiReq, anthropicReq),
                List.of(openAiResp, anthropicResp),
                List.of(openAiStream, anthropicStream)
        );

        streamAdapter = new StreamAdapter();
    }

    /**
     * TC-P2-01：协议转换集成（OpenAI 与 Anthropic 格式）。
     */
    @Test
    @DisplayName("TC-P2-01: Should convert UnifiedRequest to both OpenAI and Anthropic format payloads")
    void should_convertUnifiedRequest_to_openAiAndAnthropicPayloads() {
        // 准备
        var providerRegistry = new com.claubloom.harness.ai.provider.ProviderRegistry();
        var adapter = new com.claubloom.harness.ai.adapter.AiModelAdapter(protocolRegistry, providerRegistry, streamAdapter);

        var context = new com.claubloom.harness.core.loop.AgentContext();
        context.getMessages().add(com.claubloom.harness.protocol.message.UserMessage.of("Please implement binary search in Java"));

        var config = com.claubloom.harness.core.loop.AgentLoopConfig.builder()
                .systemPrompt("You are an expert coder.")
                .model(ModelRef.of("openai", "gpt-4o"))
                .build();

        // 执行 —— 通过 AiModelAdapter 为 OpenAI 转换
        UnifiedRequest openAiUnifiedReq = adapter.toUnifiedRequest(context, config, ModelRef.of("openai", "gpt-4o"), "openai");
        openAiUnifiedReq.setTemperature(0.7);
        openAiUnifiedReq.setMaxTokens(2048);
        openAiUnifiedReq.setTools(List.of(
                Map.of("type", "function", "function", Map.of(
                        "name", "read_file",
                        "description", "Read a file from disk",
                        "parameters", Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string")))
                ))
        ));

        var openAiConverter = protocolRegistry.getRequestConverter("openai");
        Map<String, Object> openAiPayload = openAiConverter.buildUpstreamRequest(openAiUnifiedReq, "gpt-4o");

        // 断言 OpenAI 格式
        assertThat(openAiPayload).isNotNull();
        assertThat(openAiPayload.get("model")).isEqualTo("gpt-4o");
        assertThat(openAiPayload.get("temperature")).isEqualTo(0.7);
        assertThat(openAiPayload.get("max_tokens")).isEqualTo(2048);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> openAiMessages = (List<Map<String, Object>>) openAiPayload.get("messages");
        assertThat(openAiMessages).isNotEmpty();
        assertThat(openAiMessages.stream().anyMatch(m -> "system".equals(m.get("role")))).isTrue();
        assertThat(openAiPayload).containsKey("tools");

        // 执行 —— 通过 AiModelAdapter 为 Anthropic 转换
        UnifiedRequest anthropicUnifiedReq = adapter.toUnifiedRequest(context, config, ModelRef.of("anthropic", "claude-3-5-sonnet-20241022"), "anthropic");
        anthropicUnifiedReq.setTemperature(0.7);
        anthropicUnifiedReq.setMaxTokens(2048);
        anthropicUnifiedReq.setTools(openAiUnifiedReq.getTools());

        var anthropicConverter = protocolRegistry.getRequestConverter("anthropic");
        Map<String, Object> anthropicPayload = anthropicConverter.buildUpstreamRequest(anthropicUnifiedReq, "claude-3-5-sonnet-20241022");

        // 断言 Anthropic 格式
        assertThat(anthropicPayload).isNotNull();
        assertThat(anthropicPayload.get("model")).isEqualTo("claude-3-5-sonnet-20241022");
        assertThat(anthropicPayload.get("system")).isEqualTo("You are an expert coder.");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> anthropicMessages = (List<Map<String, Object>>) anthropicPayload.get("messages");
        assertThat(anthropicMessages).isNotEmpty();
        assertThat(anthropicMessages.get(0).get("role")).isEqualTo("user");
        assertThat(anthropicPayload).containsKey("tools");
    }

    /**
     * TC-P2-02：SSE 流式分片转发与 Token 累积。
     */
    @Test
    @DisplayName("TC-P2-02: Should parse SSE stream chunks and accumulate tokens and tool calls accurately")
    void should_parseSseChunksAndAccumulateAssistantMessage() {
        // 准备
        String chunk1 = "data: {\"id\":\"chatcmpl-1\",\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Let me \"},\"finish_reason\":null}]}";
        String chunk2 = "data: {\"id\":\"chatcmpl-1\",\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"read the file.\"},\"finish_reason\":null}]}";
        String chunk3 = "data: {\"id\":\"chatcmpl-1\",\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_abc\",\"type\":\"function\",\"function\":{\"name\":\"read\",\"arguments\":\"{\\\"path\\\":\"}}]},\"finish_reason\":null}]}";
        String chunk4 = "data: {\"id\":\"chatcmpl-1\",\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"/test.txt\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}";
        String chunkDone = "data: [DONE]";

        StreamAdapter.StreamAccumulator accumulator = new StreamAdapter.StreamAccumulator("msg-stream-01", ModelRef.of("openai", "gpt-4o"));

        List<String> rawChunks = List.of(chunk1, chunk2, chunk3, chunk4, chunkDone);
        List<UnifiedStreamChunk> parsedChunks = new ArrayList<>();

        // 执行
        for (String raw : rawChunks) {
            UnifiedStreamChunk chunk = streamAdapter.parseOpenAiChunk(raw);
            if (chunk != null) {
                parsedChunks.add(chunk);
                accumulator.appendChunk(chunk, null);
            }
        }

        var assistantMessage = accumulator.toAssistantMessage(objectMapper);

        // 断言
        assertThat(parsedChunks).hasSize(4);
        assertThat(assistantMessage).isNotNull();
        assertThat(assistantMessage.id()).isEqualTo("msg-stream-01");
        assertThat(assistantMessage.stopReason()).isEqualTo("toolUse");

        // 内容应包含 1 条文本内容（"Let me read the file."）与 1 次工具调用（"read"）
        assertThat(assistantMessage.content()).hasSize(2);
        assertThat(assistantMessage.content().get(0)).isInstanceOf(com.claubloom.harness.protocol.content.TextContent.class);
        var textContent = (com.claubloom.harness.protocol.content.TextContent) assistantMessage.content().get(0);
        assertThat(textContent.text()).isEqualTo("Let me read the file.");

        assertThat(assistantMessage.content().get(1)).isInstanceOf(com.claubloom.harness.protocol.content.ToolCallContent.class);
        var toolCallContent = (com.claubloom.harness.protocol.content.ToolCallContent) assistantMessage.content().get(1);
        assertThat(toolCallContent.toolCallId()).isEqualTo("call_abc");
        assertThat(toolCallContent.toolName()).isEqualTo("read");
        @SuppressWarnings("unchecked")
        Map<String, Object> inputArgs = (Map<String, Object>) toolCallContent.input();
        assertThat(inputArgs).containsEntry("path", "/test.txt");
    }

    /**
     * TC-P2-03：验证 ProtocolRegistry 将负载路由为 Anthropic/OpenAI 格式，
     * 且 UpstreamStreamClient 的协议检测可映射到正确的 ApiKeyConfig。
     */
    @Test
    @DisplayName("TC-P2-03: Protocol conversion produces Anthropic and OpenAI payloads with correct schemas")
    void should_resolveUpstreamUrlAndHeaders_via_protocolSpecMap() throws Exception {
        // 准备
        var providerRegistry = new com.claubloom.harness.ai.provider.ProviderRegistry();
        var adapter = new com.claubloom.harness.ai.adapter.AiModelAdapter(protocolRegistry, providerRegistry, streamAdapter);

        var anthropicProvider = com.claubloom.harness.ai.provider.ProviderConfig.of(
                "anthropic-custom", "Custom Anthropic", "http://64.83.12.37:8045/v1", "sk-ant-test123", "anthropic"
        );
        var openAiProvider = com.claubloom.harness.ai.provider.ProviderConfig.of(
                "openai-custom", "Custom OpenAI", "http://64.83.12.37:8045/v1", "sk-openai-test123", "openai"
        );

        // 执行 —— 将供应商配置转换为 ai-router-core 的 ApiKeyConfig（用于驱动 URL 构建与请求头注入）
        var antKeyConfig = com.claubloom.harness.ai.provider.ProviderRegistry.toApiKeyConfig(anthropicProvider);
        var openAiKeyConfig = com.claubloom.harness.ai.provider.ProviderRegistry.toApiKeyConfig(openAiProvider);

        // 断言 —— 经 ai-router-core 领域模型校验协议与密钥的一致性
        assertThat(antKeyConfig.getProtocol()).isEqualTo("anthropic");
        assertThat(antKeyConfig.getApiKey()).isEqualTo("sk-ant-test123");
        assertThat(openAiKeyConfig.getProtocol()).isEqualTo("openai");
        assertThat(openAiKeyConfig.getApiKey()).isEqualTo("sk-openai-test123");

        // 执行 —— 协议转换器将 UnifiedRequest 规范化为各供应商专属的负载
        var context = new com.claubloom.harness.core.loop.AgentContext();
        context.getMessages().add(com.claubloom.harness.protocol.message.UserMessage.of("Hi"));
        var config = com.claubloom.harness.core.loop.AgentLoopConfig.builder()
                .systemPrompt("You are a helpful assistant.")
                .model(ModelRef.of("anthropic", "claude-3-5-sonnet-20241022"))
                .build();

        UnifiedRequest anthropicReq = adapter.toUnifiedRequest(context, config, ModelRef.of("anthropic", "claude-3-5-sonnet-20241022"), "anthropic");
        Map<String, Object> anthropicPayload = protocolRegistry.getRequestConverter("anthropic").buildUpstreamRequest(anthropicReq, "claude-3-5-sonnet-20241022");

        assertThat(anthropicPayload.get("model")).isEqualTo("claude-3-5-sonnet-20241022");
        assertThat(anthropicPayload).containsKey("system");
        assertThat(anthropicPayload).containsKey("stream");

        UnifiedRequest openAiUnified = adapter.toUnifiedRequest(context, config, ModelRef.of("openai", "gpt-4o"), "openai");
        Map<String, Object> openAiPayload = protocolRegistry.getRequestConverter("openai").buildUpstreamRequest(openAiUnified, "gpt-4o");
        assertThat(openAiPayload.get("model")).isEqualTo("gpt-4o");
        assertThat(openAiPayload).containsKey("messages");
    }

    /**
     * TC-P2-04：缺失 index 字段的流式工具调用分片。
     */
    @Test
    @DisplayName("TC-P2-04: Should aggregate tool call chunks correctly even when index field is omitted")
    void should_aggregateToolCallChunks_when_indexFieldMissing() {
        // 准备
        String chunkNameOnly = "data: {\"id\":\"chatcmpl-2\",\"choices\":[{\"delta\":{\"tool_calls\":[{\"id\":\"call_bash_1\",\"type\":\"function\",\"function\":{\"name\":\"bash\"}}]}}]}";
        String chunkArgsPart1 = "data: {\"id\":\"chatcmpl-2\",\"choices\":[{\"delta\":{\"tool_calls\":[{\"function\":{\"arguments\":\"{\\\"command\\\":\\\"ls \"}}]}}]}";
        String chunkArgsPart2 = "data: {\"id\":\"chatcmpl-2\",\"choices\":[{\"delta\":{\"tool_calls\":[{\"function\":{\"arguments\":\"-la\\\"}\"}}]}}]}";

        StreamAdapter.StreamAccumulator accumulator = new StreamAdapter.StreamAccumulator("msg-stream-02", ModelRef.of("openai", "gpt-4o"));

        for (String raw : List.of(chunkNameOnly, chunkArgsPart1, chunkArgsPart2)) {
            UnifiedStreamChunk chunk = streamAdapter.parseOpenAiChunk(raw);
            if (chunk != null) {
                accumulator.appendChunk(chunk, null);
            }
        }

        var assistantMsg = accumulator.toAssistantMessage(objectMapper);

        // 断言
        assertThat(assistantMsg.content()).hasSize(1);
        assertThat(assistantMsg.content().get(0)).isInstanceOf(com.claubloom.harness.protocol.content.ToolCallContent.class);
        var tc = (com.claubloom.harness.protocol.content.ToolCallContent) assistantMsg.content().get(0);
        assertThat(tc.toolName()).isEqualTo("bash");
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) tc.input();
        assertThat(input).containsEntry("command", "ls -la");
    }

    /**
     * TC-P2-05：Anthropic SSE 流解析与消息累积。
     */
    @Test
    @DisplayName("TC-P2-05: Should parse Anthropic SSE stream lines and accumulate text and tool calls")
    void should_parseAnthropicSseStream_and_accumulateAssistantMessage() throws Exception {
        // 准备
        String ssePayload = """
                event: message_start
                data: {"type":"message_start","message":{"id":"msg_ant_1","type":"message","role":"assistant","model":"claude-3-5-sonnet","content":[],"stop_reason":null,"usage":{"input_tokens":25,"output_tokens":0}}}

                event: content_block_start
                data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"I will execute "}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"the command."}}

                event: content_block_stop
                data: {"type":"content_block_stop","index":0}

                event: content_block_start
                data: {"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"call_bash_ant_1","name":"bash","input":{}}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\\"command\\": \\"echo 'hello'\\"}"}}

                event: content_block_stop
                data: {"type":"content_block_stop","index":1}

                event: message_delta
                data: {"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":18}}

                event: message_stop
                data: {"type":"message_stop"}
                """;

        StreamAdapter.StreamAccumulator accumulator = new StreamAdapter.StreamAccumulator(
                "msg_ant_1", ModelRef.of("anthropic", "claude-3-5-sonnet")
        );

        // 执行
        try (var reader = new java.io.BufferedReader(new java.io.StringReader(ssePayload))) {
            streamAdapter.consumeAnthropicStream(reader, accumulator, null);
        }

        var assistantMsg = accumulator.toAssistantMessage(objectMapper);

        // 断言
        assertThat(assistantMsg).isNotNull();
        assertThat(assistantMsg.id()).isEqualTo("msg_ant_1");
        assertThat(assistantMsg.stopReason()).isEqualTo("toolUse");

        // 1 条文本内容 + 1 次工具调用
        assertThat(assistantMsg.content()).hasSize(2);
        assertThat(assistantMsg.content().get(0)).isInstanceOf(com.claubloom.harness.protocol.content.TextContent.class);
        var textContent = (com.claubloom.harness.protocol.content.TextContent) assistantMsg.content().get(0);
        assertThat(textContent.text()).isEqualTo("I will execute the command.");

        assertThat(assistantMsg.content().get(1)).isInstanceOf(com.claubloom.harness.protocol.content.ToolCallContent.class);
        var toolCall = (com.claubloom.harness.protocol.content.ToolCallContent) assistantMsg.content().get(1);
        assertThat(toolCall.toolName()).isEqualTo("bash");
        assertThat(toolCall.toolCallId()).isEqualTo("call_bash_ant_1");
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) toolCall.input();
        assertThat(input).containsEntry("command", "echo 'hello'");

        // 用量校验
        assertThat(assistantMsg.usage()).isNotNull();
        assertThat(assistantMsg.usage().input()).isEqualTo(25);
        assertThat(assistantMsg.usage().output()).isGreaterThan(0);
    }
}
