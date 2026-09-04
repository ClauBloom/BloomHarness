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

            // 3. Tool Calls Delta
            if (chunk.getToolCalls() != null && !chunk.getToolCalls().isEmpty()) {
                for (Map<String, Object> tcMap : chunk.getToolCalls()) {
                    int index = tcMap.containsKey("index") ? ((Number) tcMap.get("index")).intValue() : toolCallBuilders.size();
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
     */
    public UnifiedStreamChunk parseOpenAiChunk(String jsonLine) {
        try {
            if (jsonLine.startsWith("data: ")) {
                jsonLine = jsonLine.substring(6).trim();
            }
            if (jsonLine.equals("[DONE]") || jsonLine.isBlank()) {
                return null;
            }

            JsonNode node = objectMapper.readTree(jsonLine);
            String id = node.path("id").asText(null);
            String model = node.path("model").asText(null);

            JsonNode choices = node.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode firstChoice = choices.get(0);
                JsonNode delta = firstChoice.path("delta");
                String content = delta.path("content").asText(null);
                String reasoning = delta.path("reasoning_content").asText(null);
                String finishReason = firstChoice.path("finish_reason").asText(null);

                List<Map<String, Object>> toolCalls = null;
                JsonNode toolCallsNode = delta.path("tool_calls");
                if (toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
                    toolCalls = objectMapper.convertValue(toolCallsNode, new TypeReference<>() {});
                }

                return UnifiedStreamChunk.builder()
                        .id(id)
                        .model(model)
                        .deltaContent(content)
                        .reasoningContent(reasoning)
                        .toolCalls(toolCalls)
                        .finishReason(finishReason)
                        .timestamp(System.currentTimeMillis())
                        .build();
            }
        } catch (Exception e) {
            log.warn("Failed to parse OpenAI chunk: {}", jsonLine, e);
        }
        return null;
    }
}
