package com.claubloom.harness.ai.adapter;

import com.claubloom.harness.ai.exception.AiExceptions;
import com.claubloom.harness.ai.exception.BloomAiException;
import com.claubloom.harness.ai.provider.ProviderConfig;
import com.claubloom.harness.ai.provider.ProviderRegistry;
import com.claubloom.harness.core.event.MessageUpdateEvent;
import com.claubloom.harness.core.loop.AgentContext;
import com.claubloom.harness.core.loop.AgentEventSink;
import com.claubloom.harness.core.loop.AgentLoopConfig;
import com.claubloom.harness.core.loop.LlmCaller;
import com.claubloom.harness.protocol.content.ImageContent;
import com.claubloom.harness.protocol.content.MessageContent;
import com.claubloom.harness.protocol.content.TextContent;
import com.claubloom.harness.protocol.content.ThinkingContent;
import com.claubloom.harness.protocol.content.ToolCallContent;
import com.claubloom.harness.protocol.message.AgentMessage;
import com.claubloom.harness.protocol.message.AssistantMessage;
import com.claubloom.harness.protocol.message.ToolResultMessage;
import com.claubloom.harness.protocol.message.UserMessage;
import com.claubloom.harness.protocol.model.ModelCost;
import com.claubloom.harness.protocol.model.ModelRef;
import com.claubloom.harness.protocol.session.Usage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniapi.router.core.domain.AgentIdentity;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.core.exception.UpstreamException;
import com.miniapi.router.core.springai.ChatModelRouter;
import com.miniapi.router.core.springai.RouterChatOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.content.Media;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 ai-router-core Spring AI 桥接层的 LlmCaller 实现（模型适配层唯一入口）。
 * <p>
 * 调用链：{@code LlmCaller.call} → 构建 Spring AI {@link Prompt}（含工具 schema 声明）→
 * {@link ChatModelRouter#stream(Prompt)}（ai-router-core 完成路由决策、openai/anthropic 协议转换、
 * 故障回退链与 Key 冷却）→ 消费 {@link Flux}<ChatResponse> → 聚合为协议 {@link AssistantMessage}
 * 并向事件汇发射 text/thinking/toolCall 增量。
 * <p>
 * 关键约束：{@code RouterChatOptions.internalToolExecutionEnabled=false} —— 禁止 ChatModel
 * 内部执行工具，ReAct 循环仍由核心模块 AgentLoop/ToolExecutor/PathSandbox 掌控。
 */
@Slf4j
@Component
public class SpringAiModelAdapter implements LlmCaller {

    /** 从引擎错误消息中还原上游 HTTP 状态码（UpstreamException 的消息格式："Upstream returned NNN: ..."） */
    private static final Pattern UPSTREAM_STATUS_PATTERN = Pattern.compile("Upstream returned (\\d{3})");

    private final ChatModelRouter chatModelRouter;
    private final ProviderRegistry providerRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpringAiModelAdapter(ChatModelRouter chatModelRouter, ProviderRegistry providerRegistry) {
        this.chatModelRouter = chatModelRouter;
        this.providerRegistry = providerRegistry;
    }

    @Override
    public CompletableFuture<AssistantMessage> call(AgentContext context, AgentLoopConfig config, AgentEventSink eventSink) {
        ModelRef configModel = config.getModel();
        ModelRef resolvedModel;

        // 智能兜底：会话未绑定模型时，取第一个已配置供应商的首个模型（路由引擎负责后续选择）
        if (configModel == null || configModel.provider() == null || configModel.provider().isBlank()) {
            resolvedModel = fallbackModelRef();
            if (resolvedModel == null) {
                CompletableFuture<AssistantMessage> failed = new CompletableFuture<>();
                failed.completeExceptionally(new IllegalStateException(
                        "尚未配置或选择 AI 供应商/模型，请点击设置(⚙️)配置 BaseURL、API Key 并选择模型"));
                return failed;
            }
            log.info("Session context model was unassigned; automatically fell back to provider [{}] and model [{}]",
                    resolvedModel.provider(), resolvedModel.id());
        } else {
            resolvedModel = configModel;
        }

        final ModelRef modelRef = resolvedModel;
        String messageId = UUID.randomUUID().toString();
        Prompt prompt = toSpringPrompt(context, config, modelRef);
        ChatStreamAccumulator accumulator = new ChatStreamAccumulator(messageId, modelRef);

        CompletableFuture<AssistantMessage> future = new CompletableFuture<>();
        Disposable disposable = chatModelRouter.stream(prompt).subscribe(
                // 每个流式分片：聚合 + 发射事件
                response -> accumulator.accept(response, eventSink),
                // 引擎错误（路由失败/上游异常）→ 诊断卡片映射
                error -> {
                    log.error("Failed to stream upstream AI response via ChatModelRouter", error);
                    BloomAiException mapped = error instanceof RouterException re
                            ? mapRouterException(re)
                            : new BloomAiException("AI_CALL_FAILED", 502, "调用上游异常: " + error.getMessage(),
                                    "请检查网络连通性与供应商服务状态。", false, "");
                    future.complete(handleFailure(messageId, modelRef, eventSink, mapped));
                },
                // 流正常结束 → 聚合完整助手消息
                () -> {
                    AssistantMessage message = accumulator.toAssistantMessage(objectMapper);
                    if (message.content().isEmpty()
                            || (message.content().get(0) instanceof TextContent tc && tc.text().isBlank())) {
                        log.warn("Upstream AI response finished with empty content, model: {}", modelRef.id());
                    }
                    future.complete(message);
                });
        // 调用被取消（用户中止）时立即释放上游流式订阅：停止接收 token 并断开上游连接，
        // 而不是等当前响应自然结束——这是"中止按钮即时生效"的关键一环
        future.whenComplete((ignored, throwable) -> disposable.dispose());
        return future;
    }

    /**
     * 会话未绑定模型时的兜底：取 ProviderRegistry 中第一个已配置 BaseURL 的供应商与其首个模型。
     */
    private ModelRef fallbackModelRef() {
        List<ProviderConfig> activeProviders = providerRegistry.list().stream()
                .filter(p -> p.baseUrl() != null && !p.baseUrl().isBlank())
                .toList();
        if (activeProviders.isEmpty()) {
            return null;
        }
        ProviderConfig provider = activeProviders.get(0);
        String model = !provider.models().isEmpty() ? provider.models().get(0) : provider.providerId();
        return new ModelRef(provider.providerId(), model);
    }

    /**
     * 将智能体上下文组装为 Spring AI Prompt：系统提示词 + 协议消息映射 + 引擎路由选项。
     */
    public Prompt toSpringPrompt(AgentContext context, AgentLoopConfig config, ModelRef modelRef) {
        List<Message> instructions = new ArrayList<>();
        if (config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
            instructions.add(new SystemMessage(config.getSystemPrompt()));
        }
        if (context.getMessages() != null) {
            for (AgentMessage msg : context.getMessages()) {
                Message spring = toSpringMessage(msg);
                if (spring != null) {
                    instructions.add(spring);
                }
            }
        }

        RouterChatOptions options = RouterChatOptions.builder()
                .model(modelRef.id())
                .toolCallbacks(config.getTools() == null ? List.of()
                        : config.getTools().stream().map(t -> (ToolCallback) new BloomToolCallback(t, objectMapper)).toList())
                // 关键：禁止 ChatModel 内部执行工具，ReAct 循环仍由 AgentLoop/ToolExecutor 掌控
                .internalToolExecutionEnabled(false)
                // 会话身份接入引擎会话路由记忆（SessionRouteMemory）
                .agentIdentity(AgentIdentity.builder()
                        .agentId(context.getSessionId())
                        .clientName("bloom-harness")
                        .build())
                .build();
        return new Prompt(instructions, options);
    }

    /**
     * 协议消息 → Spring AI 消息。ThinkingContent 不回传历史（与旧适配器行为一致）。
     */
    Message toSpringMessage(AgentMessage msg) {
        if (msg instanceof UserMessage um) {
            StringBuilder text = new StringBuilder();
            List<Media> mediaList = new ArrayList<>();
            for (MessageContent mc : um.content()) {
                if (mc instanceof TextContent tc) {
                    text.append(tc.text());
                } else if (mc instanceof ImageContent image) {
                    mediaList.add(toMedia(image));
                }
            }
            if (mediaList.isEmpty()) {
                return new org.springframework.ai.chat.messages.UserMessage(text.toString());
            }
            return org.springframework.ai.chat.messages.UserMessage.builder()
                    .text(text.toString())
                    .media(mediaList)
                    .build();
        }
        if (msg instanceof AssistantMessage am) {
            StringBuilder text = new StringBuilder();
            List<org.springframework.ai.chat.messages.AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
            for (MessageContent mc : am.content()) {
                if (mc instanceof TextContent tc) {
                    text.append(tc.text());
                } else if (mc instanceof ToolCallContent tc) {
                    toolCalls.add(new org.springframework.ai.chat.messages.AssistantMessage.ToolCall(
                            tc.toolCallId(), "function", tc.toolName(), toJson(tc.input())));
                }
            }
            // 防御：空 assistant 消息（上游空响应事故留下的历史残留）不回传上游，
            // 否则可能持续诱发上游再次返回空响应，形成连锁中断
            if (toolCalls.isEmpty() && text.isEmpty()) {
                log.warn("Skipping empty assistant message {} when building upstream prompt", am.id());
                return null;
            }
            return org.springframework.ai.chat.messages.AssistantMessage.builder()
                    .content(text.toString())
                    .properties(Map.of("role", "assistant"))
                    .toolCalls(toolCalls)
                    .build();
        }
        if (msg instanceof ToolResultMessage trm) {
            StringBuilder text = new StringBuilder();
            for (MessageContent mc : trm.content()) {
                if (mc instanceof TextContent tc) {
                    text.append(tc.text());
                }
            }
            ToolResponseMessage.ToolResponse response =
                    new ToolResponseMessage.ToolResponse(trm.toolCallId(), trm.toolName(), text.toString());
            return ToolResponseMessage.builder().responses(List.of(response)).build();
        }
        return null;
    }

    /**
     * 协议图片内容（base64）→ Spring AI Media。base64 解码为二进制，
     * 由引擎的 SpringAiPromptConverter 转换为 data URI（多模态透传）。
     */
    private Media toMedia(ImageContent image) {
        Object data;
        try {
            data = Base64.getDecoder().decode(image.data());
        } catch (IllegalArgumentException e) {
            data = image.data();
        }
        return Media.builder()
                .mimeType(MimeTypeUtils.parseMimeType(image.mimeType()))
                .data(data)
                .build();
    }

    private String toJson(Object input) {
        if (input == null) {
            return "{}";
        }
        if (input instanceof String str) {
            return str;
        }
        try {
            return objectMapper.writeValueAsString(input);
        } catch (Exception e) {
            log.warn("Failed to serialize tool call input to JSON: {}", input, e);
            return "{}";
        }
    }

    /* ---------- 失败处理与错误映射 ---------- */

    private AssistantMessage handleFailure(String messageId, ModelRef modelRef, AgentEventSink eventSink,
                                           BloomAiException ex) {
        log.error("AI invocation failed with domain error [{}]: {}", ex.getErrorCode(), ex.getMessage());
        if (eventSink != null) {
            try {
                String details = ex.getDetails() != null && !ex.getDetails().isBlank()
                        ? "\n\n```json\n" + ex.getDetails() + "\n```\n" : "";
                eventSink.emit(new MessageUpdateEvent(messageId, 0, "text",
                        "\n\n⚠️ **" + ex.getMessage() + "**\n\n> 💡 **排查建议**: " + ex.getSuggestion() + details));
            } catch (Exception ignored) {
            }
        }
        return AssistantMessage.error(
                messageId,
                List.of(new TextContent(ex.getMessage() + "\n建议: " + ex.getSuggestion())),
                modelRef,
                null,
                System.currentTimeMillis(),
                ex.getMessage()
        );
    }

    /**
     * 引擎异常 → BloomAiException 诊断卡片。保留旧适配器的 401/402/429/400/5xx 中文映射。
     */
    BloomAiException mapRouterException(RouterException e) {
        int status = e.getHttpStatus();
        String body = e.getMessage() == null ? "" : e.getMessage();
        if (e instanceof UpstreamException ue && ue.getUpstreamStatus() > 0) {
            status = ue.getUpstreamStatus();
        } else {
            Matcher m = UPSTREAM_STATUS_PATTERN.matcher(body);
            if (m.find()) {
                status = Integer.parseInt(m.group(1));
            }
        }
        String friendly = extractErrorMessage(body, status);
        /* 路由级错误码优先于 HTTP 状态分支（流式 onError 的 httpStatus 恒为 502） */
        String errorCode = e.getErrorCode();
        if ("NO_ROUTE_MATCHED".equals(errorCode) || "NO_AVAILABLE_UPSTREAM".equals(errorCode)
                || "ALL_UPSTREAM_FAILED".equals(errorCode)) {
            return mapByErrorCode(e, status, friendly, body);
        }
        return switch (status) {
            case 401, 403 -> new AiExceptions.AuthException(status,
                    "上游鉴权失败 (HTTP " + status + "): " + friendly, body);
            case 402 -> new AiExceptions.QuotaExhaustedException(
                    "服务商账户余额已耗尽 (HTTP 402): " + friendly, body);
            case 429 -> new AiExceptions.RateLimitException(
                    "触发服务商调用频率或并发限制 (HTTP 429): " + friendly, body);
            case 400 -> new AiExceptions.BadRequestException(
                    "请求参数错误或模型不存在 (HTTP 400): " + friendly, body);
            case 500, 502, 503, 504 -> new AiExceptions.UpstreamServerException(status,
                    "服务商服务端内部异常 (HTTP " + status + "): " + friendly, body);
            default -> mapByErrorCode(e, status, friendly, body);
        };
    }

    private BloomAiException mapByErrorCode(RouterException e, int status, String friendly, String body) {
        String errorCode = e.getErrorCode();
        if ("NO_ROUTE_MATCHED".equals(errorCode)) {
            return new BloomAiException("AI_NO_ROUTE", 404,
                    "无匹配路由: 模型未在任何已配置供应商中声明", "请点击设置(⚙️)配置供应商并拉取模型列表。", false, body);
        }
        if ("NO_AVAILABLE_UPSTREAM".equals(errorCode) || "ALL_UPSTREAM_FAILED".equals(errorCode)) {
            // 回退链耗尽后，若能还原出请求级上游状态码（401/402/429/400），优先给出具体诊断而非笼统的"无可用上游"
            return switch (status) {
                case 401, 403 -> new AiExceptions.AuthException(status,
                        "上游鉴权失败 (HTTP " + status + "): " + friendly, body);
                case 402 -> new AiExceptions.QuotaExhaustedException(
                        "服务商账户余额已耗尽 (HTTP 402): " + friendly, body);
                case 429 -> new AiExceptions.RateLimitException(
                        "触发服务商调用频率或并发限制 (HTTP 429): " + friendly, body);
                case 400 -> new AiExceptions.BadRequestException(
                        "请求参数错误或模型不存在 (HTTP 400): " + friendly, body);
                default -> new BloomAiException("AI_NO_UPSTREAM", 503,
                        "无可用上游: " + friendly, "所有供应商均不可用，请稍后重试或检查供应商配置。", true, body);
            };
        }
        if (errorCode != null && !"UPSTREAM_ERROR".equals(errorCode)) {
            return new BloomAiException("AI_UPSTREAM_" + errorCode, status > 0 ? status : 502,
                    "上游响应异常 (" + errorCode + "): " + friendly, "请根据错误信息排查供应商状态。", false, body);
        }
        return new BloomAiException("AI_UPSTREAM_HTTP_" + (status > 0 ? status : 502), status > 0 ? status : 502,
                "上游响应异常 (HTTP " + (status > 0 ? status : 502) + "): " + friendly,
                "请根据上方返回的错误信息排查供应商状态。", false, body);
    }

    private String extractErrorMessage(String errorBody, int statusCode) {
        if (errorBody == null || errorBody.isBlank()) {
            return "HTTP " + statusCode;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(errorBody);
            if (root.has("error") && root.get("error").has("message")) {
                return root.get("error").get("message").asText();
            }
        } catch (Exception ignored) {
        }
        return errorBody.length() > 300 ? errorBody.substring(0, 300) + "..." : errorBody;
    }

    /* ---------- 内嵌组件 ---------- */

    /**
     * 流式 ChatResponse 累加器：把 Spring AI 流式分片聚合为协议 AssistantMessage，
     * 同时向事件汇发射 text/thinking/toolCall 增量事件。
     * <p>
     * index 约定与旧 StreamAdapter 保持一致：0=text / 1=thinking / (toolCallOrdinal + 2)=toolCall，
     * 前端渲染逻辑零改动。推理内容经 ai-router-core 补丁从 AssistantMessage metadata 透传；
     * 内嵌 &lt;think&gt;&lt;/think&gt; 标签沿用旧状态机动态分流。
     */
    static class ChatStreamAccumulator {

        private final String messageId;
        private final ModelRef model;
        private final StringBuilder textBuilder = new StringBuilder();
        private final StringBuilder thinkingBuilder = new StringBuilder();
        private final Map<String, ToolCallBuilder> toolCallBuilders = new LinkedHashMap<>();
        private final AtomicInteger promptTokens = new AtomicInteger(0);
        private final AtomicInteger completionTokens = new AtomicInteger(0);
        private volatile String finishReason = "";
        private volatile String responseModel;
        private ToolCallBuilder lastToolCallBuilder;

        // <think>...</think> 标签状态机：识别混在文本增量中的思考内容
        private boolean inThinkTag = false;

        ChatStreamAccumulator(String messageId, ModelRef model) {
            this.messageId = messageId;
            this.model = model;
        }

        void accept(ChatResponse response, AgentEventSink eventSink) {
            if (response == null) {
                return;
            }
            ChatResponseMetadata metadata = response.getMetadata();
            if (response.getResults() == null || response.getResults().isEmpty()) {
                // 收尾 usage 块（空 generations，对齐 include_usage 语义）
                if (metadata != null) {
                    captureUsage(metadata);
                }
                return;
            }
            if (metadata != null) {
                captureUsage(metadata);
            }
            for (Generation generation : response.getResults()) {
                if (generation == null) {
                    continue;
                }
                ChatGenerationMetadata generationMeta = generation.getMetadata();
                if (generationMeta != null && generationMeta.getFinishReason() != null
                        && !generationMeta.getFinishReason().isEmpty()) {
                    finishReason = generationMeta.getFinishReason();
                }
                org.springframework.ai.chat.messages.AssistantMessage output = generation.getOutput();
                if (output == null) {
                    continue;
                }
                Object reasoning = output.getMetadata() != null
                        ? output.getMetadata().get("reasoningContent") : null;
                if (reasoning instanceof String r && !r.isEmpty()) {
                    emitThinking(r, eventSink);
                }
                String text = output.getText();
                if (text != null && !text.isEmpty()) {
                    processDeltaContent(text, eventSink);
                }
                if (output.getToolCalls() != null && !output.getToolCalls().isEmpty()) {
                    for (org.springframework.ai.chat.messages.AssistantMessage.ToolCall tc : output.getToolCalls()) {
                        mergeToolCallDelta(tc, eventSink);
                    }
                }
            }
        }

        private void captureUsage(ChatResponseMetadata metadata) {
            if (metadata.getUsage() != null) {
                int prompt = (int) metadata.getUsage().getPromptTokens();
                int completion = (int) metadata.getUsage().getCompletionTokens();
                if (prompt > 0) {
                    promptTokens.set(prompt);
                }
                if (completion > 0) {
                    completionTokens.set(completion);
                }
            }
            if (metadata.getModel() != null && !metadata.getModel().isBlank()) {
                responseModel = metadata.getModel();
            }
        }

        /**
         * 归集工具调用增量。Spring AI 的 ToolCall 不携带 index（OpenAI delta 的 index 已在引擎
         * 聚合层消费），按 id 归集；缺失 id 的后续分片归入最近一次构建中的调用。
         */
        private void mergeToolCallDelta(org.springframework.ai.chat.messages.AssistantMessage.ToolCall tc,
                                        AgentEventSink eventSink) {
            ToolCallBuilder builder;
            if (tc.id() != null && !tc.id().isEmpty()) {
                builder = toolCallBuilders.computeIfAbsent(tc.id(), k -> new ToolCallBuilder(toolCallBuilders.size()));
                lastToolCallBuilder = builder;
            } else if (lastToolCallBuilder != null) {
                builder = lastToolCallBuilder;
            } else {
                builder = new ToolCallBuilder(toolCallBuilders.size());
                toolCallBuilders.put(UUID.randomUUID().toString(), builder);
                lastToolCallBuilder = builder;
            }

            boolean changed = false;
            if (tc.name() != null && !tc.name().isEmpty() && !tc.name().equals(builder.name)) {
                builder.name = tc.name();
                changed = true;
            }
            if (tc.id() != null && !tc.id().isEmpty() && !tc.id().equals(builder.id)) {
                builder.id = tc.id();
            }
            String fragment = tc.arguments();
            if (fragment != null && !fragment.isEmpty()) {
                builder.arguments.append(fragment);
                changed = true;
            }
            if (changed && eventSink != null) {
                try {
                    eventSink.emit(new MessageUpdateEvent(messageId, builder.ordinal + 2, "toolCall",
                            fragment != null ? fragment : builder.name));
                } catch (Exception e) {
                    log.warn("Error emitting toolCall update event", e);
                }
            }
        }

        private void processDeltaContent(String rawText, AgentEventSink eventSink) {
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

        AssistantMessage toAssistantMessage(ObjectMapper mapper) {
            List<MessageContent> contents = new ArrayList<>();
            if (!thinkingBuilder.isEmpty()) {
                contents.add(new ThinkingContent(thinkingBuilder.toString(), false));
            }
            if (!textBuilder.isEmpty()) {
                contents.add(new TextContent(textBuilder.toString()));
            }
            for (ToolCallBuilder tc : toolCallBuilders.values()) {
                Object inputObj;
                try {
                    String argsJson = tc.arguments.toString().trim();
                    inputObj = argsJson.isEmpty()
                            ? Map.of()
                            : mapper.readValue(argsJson, new TypeReference<Map<String, Object>>() {
                            });
                } catch (Exception e) {
                    inputObj = Map.of("raw", tc.arguments.toString());
                }
                contents.add(new ToolCallContent(
                        tc.id != null ? tc.id : UUID.randomUUID().toString(),
                        tc.name != null ? tc.name : "unknown_tool",
                        inputObj
                ));
            }

            String stopReason = switch (finishReason) {
                case "TOOL_CALLS" -> "toolUse";
                case "LENGTH" -> "length";
                case "CONTENT_FILTER" -> "contentFilter";
                case "" -> "stop";
                default -> finishReason.toLowerCase();
            };
            int pTokens = promptTokens.get();
            int cTokens = completionTokens.get();
            Usage usage = new Usage(pTokens, cTokens, 0, 0, null, pTokens + cTokens, ModelCost.zero());

            return AssistantMessage.complete(
                    messageId,
                    contents,
                    model,
                    responseModel != null ? responseModel : model.id(),
                    usage,
                    System.currentTimeMillis(),
                    stopReason
            );
        }
    }

    private static class ToolCallBuilder {
        final int ordinal;
        String id;
        String name;
        final StringBuilder arguments = new StringBuilder();

        ToolCallBuilder(int ordinal) {
            this.ordinal = ordinal;
        }
    }

    /**
     * 将 BloomHarness 工具定义包装为 Spring AI ToolCallback。
     * 仅用于向模型声明工具 schema；ChatModel 内部工具执行已通过
     * {@code internalToolExecutionEnabled=false} 关闭，真正执行由核心模块 ToolExecutor 负责，
     * 因此 call() 不应被调用。
     */
    static final class BloomToolCallback implements ToolCallback {

        private final com.claubloom.harness.core.tool.ToolDefinition tool;
        private final ObjectMapper mapper;

        BloomToolCallback(com.claubloom.harness.core.tool.ToolDefinition tool, ObjectMapper mapper) {
            this.tool = tool;
            this.mapper = mapper;
        }

        @Override
        public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
            return org.springframework.ai.tool.definition.ToolDefinition.builder()
                    .name(tool.name())
                    .description(tool.description() != null ? tool.description() : "")
                    .inputSchema(toJsonSchema(tool.parameterSchema()))
                    .build();
        }

        @Override
        public String call(String toolInput) {
            throw new UnsupportedOperationException("工具由 BloomHarness ToolExecutor 执行，ChatModel 内部执行已禁用");
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            throw new UnsupportedOperationException("工具由 BloomHarness ToolExecutor 执行，ChatModel 内部执行已禁用");
        }

        /** BloomHarness 的 parameterSchema 为 Map，Spring AI 的 inputSchema 为 JSON Schema 字符串 */
        private String toJsonSchema(Map<String, Object> schema) {
            if (schema == null || schema.isEmpty()) {
                return "{\"type\":\"object\"}";
            }
            try {
                return mapper.writeValueAsString(schema);
            } catch (Exception e) {
                return "{\"type\":\"object\"}";
            }
        }
    }
}
