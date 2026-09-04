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
 * Phase 2 Smoke Test for AI Model Adapter (TC-P2-01, TC-P2-02).
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
     * TC-P2-01: Protocol Conversion Integration (OpenAI & Anthropic formats).
     */
    @Test
    @DisplayName("TC-P2-01: Should convert UnifiedRequest to both OpenAI and Anthropic format payloads")
    void should_convertUnifiedRequest_to_openAiAndAnthropicPayloads() {
        // Arrange
        var providerRegistry = new com.claubloom.harness.ai.provider.ProviderRegistry();
        var adapter = new com.claubloom.harness.ai.adapter.AiModelAdapter(protocolRegistry, providerRegistry, streamAdapter);

        var context = new com.claubloom.harness.core.loop.AgentContext();
        context.getMessages().add(com.claubloom.harness.protocol.message.UserMessage.of("Please implement binary search in Java"));

        var config = com.claubloom.harness.core.loop.AgentLoopConfig.builder()
                .systemPrompt("You are an expert coder.")
                .model(ModelRef.of("openai", "gpt-4o"))
                .build();

        // Act - Convert via AiModelAdapter for OpenAI
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

        // Assert OpenAI
        assertThat(openAiPayload).isNotNull();
        assertThat(openAiPayload.get("model")).isEqualTo("gpt-4o");
        assertThat(openAiPayload.get("temperature")).isEqualTo(0.7);
        assertThat(openAiPayload.get("max_tokens")).isEqualTo(2048);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> openAiMessages = (List<Map<String, Object>>) openAiPayload.get("messages");
        assertThat(openAiMessages).isNotEmpty();
        assertThat(openAiMessages.stream().anyMatch(m -> "system".equals(m.get("role")))).isTrue();
        assertThat(openAiPayload).containsKey("tools");

        // Act - Convert via AiModelAdapter for Anthropic
        UnifiedRequest anthropicUnifiedReq = adapter.toUnifiedRequest(context, config, ModelRef.of("anthropic", "claude-3-5-sonnet-20241022"), "anthropic");
        anthropicUnifiedReq.setTemperature(0.7);
        anthropicUnifiedReq.setMaxTokens(2048);
        anthropicUnifiedReq.setTools(openAiUnifiedReq.getTools());

        var anthropicConverter = protocolRegistry.getRequestConverter("anthropic");
        Map<String, Object> anthropicPayload = anthropicConverter.buildUpstreamRequest(anthropicUnifiedReq, "claude-3-5-sonnet-20241022");

        // Assert Anthropic
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
     * TC-P2-02: SSE Streaming Chunk Proxy and Token Accumulation.
     */
    @Test
    @DisplayName("TC-P2-02: Should parse SSE stream chunks and accumulate tokens and tool calls accurately")
    void should_parseSseChunksAndAccumulateAssistantMessage() {
        // Arrange
        String chunk1 = "data: {\"id\":\"chatcmpl-1\",\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Let me \"},\"finish_reason\":null}]}";
        String chunk2 = "data: {\"id\":\"chatcmpl-1\",\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"read the file.\"},\"finish_reason\":null}]}";
        String chunk3 = "data: {\"id\":\"chatcmpl-1\",\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_abc\",\"type\":\"function\",\"function\":{\"name\":\"read\",\"arguments\":\"{\\\"path\\\":\"}}]},\"finish_reason\":null}]}";
        String chunk4 = "data: {\"id\":\"chatcmpl-1\",\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"/test.txt\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}";
        String chunkDone = "data: [DONE]";

        StreamAdapter.StreamAccumulator accumulator = new StreamAdapter.StreamAccumulator("msg-stream-01", ModelRef.of("openai", "gpt-4o"));

        List<String> rawChunks = List.of(chunk1, chunk2, chunk3, chunk4, chunkDone);
        List<UnifiedStreamChunk> parsedChunks = new ArrayList<>();

        // Act
        for (String raw : rawChunks) {
            UnifiedStreamChunk chunk = streamAdapter.parseOpenAiChunk(raw);
            if (chunk != null) {
                parsedChunks.add(chunk);
                accumulator.appendChunk(chunk, null);
            }
        }

        var assistantMessage = accumulator.toAssistantMessage(objectMapper);

        // Assert
        assertThat(parsedChunks).hasSize(4);
        assertThat(assistantMessage).isNotNull();
        assertThat(assistantMessage.id()).isEqualTo("msg-stream-01");
        assertThat(assistantMessage.stopReason()).isEqualTo("toolUse");

        // Contents should have 1 text content ("Let me read the file.") and 1 tool call ("read")
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
}
