package com.claubloom.harness.ai.adapter;

import com.claubloom.harness.core.event.MessageUpdateEvent;
import com.claubloom.harness.core.loop.AgentEventSink;
import com.claubloom.harness.protocol.content.MessageContent;
import com.claubloom.harness.protocol.content.TextContent;
import com.claubloom.harness.protocol.content.ThinkingContent;
import com.claubloom.harness.protocol.content.ToolCallContent;
import com.claubloom.harness.protocol.message.AssistantMessage;
import com.claubloom.harness.protocol.model.ModelCost;
import com.claubloom.harness.protocol.model.ModelRef;
import com.claubloom.harness.protocol.session.Usage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniapi.router.core.protocol.UnifiedStreamChunk;
import com.miniapi.router.core.streaming.DeltaJsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * StreamAdapter 负责将流式数据帧（SSE chunks）解析为 UnifiedStreamChunk 并聚合成完整的 AssistantMessage。
 */
@Slf4j
@Component
public class StreamAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 用于跨多个流式分片累加收集 Token 文本与工具调用参数的累加器。
     */
    public static class StreamAccumulator {
        private final String messageId;
        private final ModelRef model;
        private final StringBuilder textBuilder = new StringBuilder();
        private final StringBuilder thinkingBuilder = new StringBuilder();
        private final Map<Integer, ToolCallBuilder> toolCallBuilders = new LinkedHashMap<>();
        private final AtomicInteger promptTokens = new AtomicInteger(0);
        private final AtomicInteger completionTokens = new AtomicInteger(0);
        private String finishReason = "stop";

        // 思考标签 <think>...</think> 状态机，用于自动识别与拆分混在 deltaContent 中的思考文本
        private boolean inThinkTag = false;

        public StreamAccumulator(String messageId, ModelRef model) {
            this.messageId = messageId;
            this.model = model;
        }

        public String getMessageId() {
            return messageId;
        }

        public void appendChunk(UnifiedStreamChunk chunk, AgentEventSink eventSink) {
            if (chunk == null) {
                return;
            }

            // 1. 原生显式 Thinking / Reasoning Delta (部分服务商直接在 delta.reasoning_content 提供)
            if (chunk.getReasoningContent() != null && !chunk.getReasoningContent().isEmpty()) {
                emitThinking(chunk.getReasoningContent(), eventSink);
            }

            // 2. 文本内容流及内嵌 <think>...</think> 动态分流
            if (chunk.getDeltaContent() != null && !chunk.getDeltaContent().isEmpty()) {
                processDeltaContent(chunk.getDeltaContent(), eventSink);
            }

            // 3. Tool Calls Delta (OpenAI 格式解析结果)
            if (chunk.getToolCalls() != null && !chunk.getToolCalls().isEmpty()) {
                for (Map<String, Object> tcMap : chunk.getToolCalls()) {
                    int index;
                    if (tcMap.containsKey("index") && tcMap.get("index") instanceof Number num) {
                        index = num.intValue();
                    } else if (!toolCallBuilders.isEmpty()) {
                        // 缺少 index 时（部分网关/代理流式分片），优先归集到当前最后一个正在构建的 ToolCallBuilder
                        index = toolCallBuilders.keySet().stream().max(Integer::compareTo).orElse(0);
                    } else {
                        index = 0;
                    }
                    ToolCallBuilder builder = toolCallBuilders.computeIfAbsent(index, i -> new ToolCallBuilder());

                    if (tcMap.get("id") != null) {
                        builder.id = (String) tcMap.get("id");
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> fn = (Map<String, Object>) tcMap.get("function");
                    if (fn != null) {
                        if (fn.get("name") != null) {
                            builder.name = (String) fn.get("name");
                        }
                        if (fn.get("arguments") != null) {
                            builder.argumentsBuilder.append((String) fn.get("arguments"));
                        }
                    }

                    if (eventSink != null && fn != null && fn.get("arguments") != null) {
                        try {
                            eventSink.emit(new MessageUpdateEvent(messageId, index + 2, "toolCall", (String) fn.get("arguments")));
                        } catch (Exception e) {
                            log.warn("Error emitting toolCall update event", e);
                        }
                    }
                }
            }

            // 4. Anthropic Tool Calls (来自 DeltaJsonParser extra 结构，仅在 tool_use 内容块上触发)
            if ("tool_use".equals(chunk.getContentType()) && chunk.getExtra() != null) {
                int index = chunk.getIndex();
                ToolCallBuilder builder = toolCallBuilders.computeIfAbsent(index, i -> new ToolCallBuilder());
                if (chunk.getExtra().containsKey("tool_use_id")) {
                    builder.id = (String) chunk.getExtra().get("tool_use_id");
                }
                if (chunk.getExtra().containsKey("tool_name")) {
                    builder.name = (String) chunk.getExtra().get("tool_name");
                }
                if (chunk.getExtra().containsKey("input_json_delta")) {
                    String partial = (String) chunk.getExtra().get("input_json_delta");
                    builder.argumentsBuilder.append(partial);
                    if (eventSink != null) {
                        try {
                            eventSink.emit(new MessageUpdateEvent(messageId, index + 2, "toolCall", partial));
                        } catch (Exception e) {
                            log.warn("Error emitting toolCall update event", e);
                        }
                    }
                }
            }

            if (chunk.getFinishReason() != null && !chunk.getFinishReason().isEmpty()) {
                this.finishReason = chunk.getFinishReason();
            }

            if (chunk.getUpstreamUsage() != null) {
                if (chunk.getUpstreamUsage().containsKey("prompt_tokens")) {
                    promptTokens.set(chunk.getUpstreamUsage().get("prompt_tokens"));
                }
                if (chunk.getUpstreamUsage().containsKey("completion_tokens")) {
                    completionTokens.set(chunk.getUpstreamUsage().get("completion_tokens"));
                }
            }
        }

        /**
         * 直接补录上游用量（覆盖语义，幂等）。用于旧版解析器丢弃 message_start 用量时的兜底补录。
         */
        public void appendUsage(Map<String, Integer> usage) {
            if (usage == null) {
                return;
            }
            if (usage.containsKey("prompt_tokens")) {
                promptTokens.set(usage.get("prompt_tokens"));
            }
            if (usage.containsKey("completion_tokens")) {
                completionTokens.set(usage.get("completion_tokens"));
            }
        }

        /**
         * 动态拆分并分流处理文本分片中的 <think> 与 </think> 标签内容。
         */
        private void processDeltaContent(String rawText, AgentEventSink eventSink) {
            if (rawText == null || rawText.isEmpty()) return;

            String text = rawText;
            while (!text.isEmpty()) {
                if (!inThinkTag) {
                    int thinkStart = text.indexOf("<think>");
                    if (thinkStart >= 0) {
                        String before = text.substring(0, thinkStart);
                        if (!before.isEmpty()) {
                            emitText(before, eventSink);
                        }
                        inThinkTag = true;
                        text = text.substring(thinkStart + 7);
                    } else {
                        emitText(text, eventSink);
                        break;
                    }
                } else {
                    int thinkEnd = text.indexOf("</think>");
                    if (thinkEnd >= 0) {
                        String thinkPart = text.substring(0, thinkEnd);
                        if (!thinkPart.isEmpty()) {
                            emitThinking(thinkPart, eventSink);
                        }
                        inThinkTag = false;
                        text = text.substring(thinkEnd + 8);
                    } else {
                        emitThinking(text, eventSink);
                        break;
                    }
                }
            }
        }

        private void emitText(String text, AgentEventSink eventSink) {
            textBuilder.append(text);
            completionTokens.incrementAndGet();
            if (eventSink != null) {
                try {
                    eventSink.emit(new MessageUpdateEvent(messageId, 0, "text", text));
                } catch (Exception e) {
                    log.warn("Error emitting text update event", e);
                }
            }
        }

        private void emitThinking(String thinking, AgentEventSink eventSink) {
            thinkingBuilder.append(thinking);
            if (eventSink != null) {
                try {
                    eventSink.emit(new MessageUpdateEvent(messageId, 1, "thinking", thinking));
                } catch (Exception e) {
                    log.warn("Error emitting thinking update event", e);
                }
            }
        }

        public AssistantMessage toAssistantMessage(ObjectMapper mapper) {
            List<MessageContent> contents = new ArrayList<>();

            if (!thinkingBuilder.isEmpty()) {
                contents.add(new ThinkingContent(thinkingBuilder.toString(), false));
            }
            if (!textBuilder.isEmpty()) {
                contents.add(new TextContent(textBuilder.toString()));
            }

            for (ToolCallBuilder tc : toolCallBuilders.values()) {
                Object inputObj = Collections.emptyMap();
                try {
                    String argsJson = tc.argumentsBuilder.toString().trim();
                    if (!argsJson.isEmpty()) {
                        inputObj = mapper.readValue(argsJson, new TypeReference<Map<String, Object>>() {});
                    }
                } catch (Exception e) {
                    inputObj = Map.of("raw", tc.argumentsBuilder.toString());
                }

                contents.add(new ToolCallContent(
                        tc.id != null ? tc.id : UUID.randomUUID().toString(),
                        tc.name != null ? tc.name : "unknown_tool",
                        inputObj
                ));
            }

            String stopReason = "tool_calls".equalsIgnoreCase(finishReason) ? "toolUse" : finishReason;
            int pTokens = promptTokens.get();
            int cTokens = completionTokens.get();
            Usage usage = new Usage(pTokens, cTokens, 0, 0, null, pTokens + cTokens, ModelCost.zero());

            return AssistantMessage.complete(
                    messageId,
                    contents,
                    model,
                    model.id(),
                    usage,
                    System.currentTimeMillis(),
                    stopReason
            );
        }
    }

    private static class ToolCallBuilder {
        String id;
        String name;
        StringBuilder argumentsBuilder = new StringBuilder();
    }

    /**
     * 将 OpenAI 原始流式 JSON 数据行解析为统一的数据分片 UnifiedStreamChunk。
     * 兼容性包装：委托给 ai-router-core 的 DeltaJsonParser，避免重复维护 SSE 解析逻辑。
     */
    public UnifiedStreamChunk parseOpenAiChunk(String jsonLine) {
        Object parsed = DeltaJsonParser.parseSseLine(jsonLine, "openai", null, null);
        if (parsed instanceof UnifiedStreamChunk chunk) {
            return chunk;
        }
        return null;
    }

    /**
     * 统一的流式消费入口：逐行读取 SSE 并委托 ai-router-core 的 DeltaJsonParser 解析。
     * 支持 OpenAI 与 Anthropic 两种上游协议。
     * <p>
     * 兼容性兜底：旧版 DeltaJsonParser 在 Anthropic message_start 事件上不携带 usage，
     * 此处直接从原始行补录 input_tokens（覆盖语义，幂等，与新版解析器不冲突）。
     */
    public void consumeStream(
            java.io.BufferedReader reader,
            String protocol,
            StreamAccumulator accumulator,
            AgentEventSink eventSink
    ) throws java.io.IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.startsWith("data:")) {
                continue;
            }
            if (accumulator != null && "anthropic".equalsIgnoreCase(protocol)
                    && trimmed.contains("\"message_start\"")) {
                accumulator.appendUsage(extractAnthropicMessageStartUsage(trimmed));
            }
            // DeltaJsonParser 期望完整的 "data: ..." SSE 行（内部自行剥离前缀）
            Object parsed = DeltaJsonParser.parseSseLine(trimmed, protocol,
                    accumulator != null ? accumulator.getMessageId() : null, null);
            if (parsed == DeltaJsonParser.DONE) {
                return;
            }
            if (parsed instanceof UnifiedStreamChunk chunk) {
                accumulator.appendChunk(chunk, eventSink);
            }
        }
    }

    /**
     * 从 Anthropic message_start 原始 data 行提取 usage（input_tokens -> prompt_tokens）。
     */
    private Map<String, Integer> extractAnthropicMessageStartUsage(String dataLine) {
        try {
            JsonNode node = objectMapper.readTree(dataLine.substring(5).trim());
            JsonNode usageNode = node.path("message").path("usage");
            if (usageNode.isMissingNode() || usageNode.size() == 0) {
                return null;
            }
            Map<String, Integer> usage = new LinkedHashMap<>();
            if (usageNode.has("input_tokens")) {
                usage.put("prompt_tokens", usageNode.get("input_tokens").asInt(0));
            }
            if (usageNode.has("output_tokens")) {
                usage.put("completion_tokens", usageNode.get("output_tokens").asInt(0));
            }
            return usage;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 消费并解析 Anthropic 协议标准的 SSE 事件流（兼容旧 API，委托 {@link #consumeStream}）。
     */
    public void consumeAnthropicStream(
            java.io.BufferedReader reader,
            StreamAccumulator accumulator,
            AgentEventSink eventSink
    ) throws java.io.IOException {
        consumeStream(reader, "anthropic", accumulator, eventSink);
    }
}
