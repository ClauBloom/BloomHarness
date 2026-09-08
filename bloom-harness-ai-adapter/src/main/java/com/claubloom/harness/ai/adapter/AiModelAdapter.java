package com.claubloom.harness.ai.adapter;

import com.claubloom.harness.ai.provider.ProviderConfig;
import com.claubloom.harness.ai.provider.ProviderRegistry;
import com.claubloom.harness.core.loop.AgentContext;
import com.claubloom.harness.core.loop.AgentEventSink;
import com.claubloom.harness.core.loop.AgentLoopConfig;
import com.claubloom.harness.core.loop.LlmCaller;
import com.claubloom.harness.protocol.content.MessageContent;
import com.claubloom.harness.protocol.content.TextContent;
import com.claubloom.harness.protocol.content.ToolCallContent;
import com.claubloom.harness.protocol.message.AgentMessage;
import com.claubloom.harness.protocol.message.AssistantMessage;
import com.claubloom.harness.protocol.message.ToolResultMessage;
import com.claubloom.harness.protocol.message.UserMessage;
import com.claubloom.harness.protocol.model.ModelRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.exception.UpstreamException;
import com.miniapi.router.core.protocol.ProtocolRegistry;
import com.miniapi.router.core.protocol.UnifiedRequest;
import com.miniapi.router.core.protocol.converter.RequestConverter;
import com.miniapi.router.core.streaming.UpstreamStreamClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * AiModelAdapter 将智能体消息转换为 UnifiedRequest 统一请求体，并通过 ai-router-core 调用上游 API。
 * 实现核心模块的 LlmCaller 接口，从而可直接驱动 AgentLoop。
 */
@Slf4j
@Component
public class AiModelAdapter implements LlmCaller {

    private final ProtocolRegistry protocolRegistry;
    private final ProviderRegistry providerRegistry;
    private final StreamAdapter streamAdapter;
    private final UpstreamStreamClient upstreamStreamClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiModelAdapter(
            ProtocolRegistry protocolRegistry,
            ProviderRegistry providerRegistry,
            StreamAdapter streamAdapter
    ) {
        this(protocolRegistry, providerRegistry, streamAdapter, new UpstreamStreamClient());
    }

    @Autowired
    public AiModelAdapter(
            ProtocolRegistry protocolRegistry,
            ProviderRegistry providerRegistry,
            StreamAdapter streamAdapter,
            UpstreamStreamClient upstreamStreamClient
    ) {
        this.protocolRegistry = protocolRegistry;
        this.providerRegistry = providerRegistry;
        this.streamAdapter = streamAdapter;
        this.upstreamStreamClient = upstreamStreamClient;
    }

    @Override
    public CompletableFuture<AssistantMessage> call(AgentContext context, AgentLoopConfig config, AgentEventSink eventSink) {
        ModelRef modelRef = config.getModel();

        // 智能兜底：如果会话未绑定特定模型，尝试使用 ProviderRegistry 中用户已配置的第一个可用供应商与模型
        if (modelRef == null || modelRef.provider() == null || modelRef.provider().isBlank()) {
            List<ProviderConfig> activeProviders = providerRegistry.list().stream()
                    .filter(p -> p.baseUrl() != null && !p.baseUrl().isBlank())
                    .toList();
            if (!activeProviders.isEmpty()) {
                ProviderConfig fallbackProvider = activeProviders.get(0);
                String fallbackModel = (fallbackProvider.models() != null && !fallbackProvider.models().isEmpty())
                        ? fallbackProvider.models().get(0)
                        : fallbackProvider.providerId();
                modelRef = new ModelRef(fallbackProvider.providerId(), fallbackModel);
                log.info("Session context model was unassigned; automatically fell back to configured provider [{}] and model [{}]",
                        modelRef.provider(), modelRef.id());
            } else {
                CompletableFuture<AssistantMessage> failed = new CompletableFuture<>();
                failed.completeExceptionally(new IllegalStateException("尚未配置或选择 AI 供应商/模型，请点击设置(⚙️)配置 BaseURL、API Key 并选择模型"));
                return failed;
            }
        }

        ProviderConfig provider = providerRegistry.find(modelRef.provider())
                .orElse(null);
        if (provider == null || provider.baseUrl() == null || provider.baseUrl().isBlank()) {
            CompletableFuture<AssistantMessage> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("供应商 [" + modelRef.provider() + "] 尚未配置有效 BaseURL 或 API Key，请先在设置中完成配置"));
            return failed;
        }

        String protocol = provider.protocol() != null ? provider.protocol() : ProtocolRegistry.inferProtocol(modelRef.provider());
        UnifiedRequest unifiedRequest = toUnifiedRequest(context, config, modelRef, protocol);

        RequestConverter requestConverter = protocolRegistry.getRequestConverter(protocol);
        Map<String, Object> upstreamPayload = requestConverter.buildUpstreamRequest(unifiedRequest, modelRef.id());

        return callUpstream(provider, protocol, upstreamPayload, modelRef, eventSink);
    }

    /**
     * 将 AgentContext 智能体上下文中的消息转换为 UnifiedRequest 统一请求体。
     */
    public UnifiedRequest toUnifiedRequest(AgentContext context, AgentLoopConfig config, ModelRef modelRef, String protocol) {
        UnifiedRequest req = new UnifiedRequest();
        req.setModel(modelRef.id());
        req.setSystemPrompt(config.getSystemPrompt());
        req.setInboundProtocol("openai");
        req.setUpstreamProtocol(protocol);
        req.setStream(true);

        List<Map<String, Object>> messagesList = new ArrayList<>();
        if ("openai".equalsIgnoreCase(protocol) && config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
            messagesList.add(Map.of("role", "system", "content", config.getSystemPrompt()));
        }
        if (context.getMessages() != null) {
            for (AgentMessage msg : context.getMessages()) {
                messagesList.add(convertAgentMessageToMap(msg));
            }
        }
        req.setMessages(messagesList);

        // 注入标准 OpenAI 格式的 tools 结构
        if (config.getTools() != null && !config.getTools().isEmpty()) {
            List<Map<String, Object>> toolsList = new ArrayList<>();
            for (var tool : config.getTools()) {
                Map<String, Object> functionMap = new LinkedHashMap<>();
                functionMap.put("name", tool.name());
                functionMap.put("description", tool.description() != null ? tool.description() : "");
                functionMap.put("parameters", tool.parameterSchema() != null ? tool.parameterSchema() : Map.of("type", "object"));
                toolsList.add(Map.of("type", "function", "function", functionMap));
            }
            req.setTools(toolsList);
        }

        return req;
    }

    private Map<String, Object> convertAgentMessageToMap(AgentMessage msg) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", msg.role());

        if (msg instanceof UserMessage um) {
            StringBuilder textContent = new StringBuilder();
            for (MessageContent mc : um.content()) {
                if (mc instanceof TextContent tc) {
                    textContent.append(tc.text());
                }
            }
            map.put("content", textContent.toString());
        } else if (msg instanceof AssistantMessage am) {
            StringBuilder textContent = new StringBuilder();
            List<Map<String, Object>> toolCalls = new ArrayList<>();

            for (MessageContent mc : am.content()) {
                if (mc instanceof TextContent tc) {
                    textContent.append(tc.text());
                } else if (mc instanceof ToolCallContent tc) {
                    String argsJson = "{}";
                    if (tc.input() != null) {
                        try {
                            argsJson = tc.input() instanceof String str ? str : objectMapper.writeValueAsString(tc.input());
                        } catch (Exception e) {
                            log.warn("Failed to serialize tool call input to JSON: {}", tc.input(), e);
                            argsJson = "{}";
                        }
                    }
                    toolCalls.add(Map.of(
                            "id", tc.toolCallId(),
                            "type", "function",
                            "function", Map.of(
                                    "name", tc.toolName(),
                                    "arguments", argsJson
                            )
                    ));
                }
            }
            map.put("content", textContent.toString());
            if (!toolCalls.isEmpty()) {
                map.put("tool_calls", toolCalls);
            }
        } else if (msg instanceof ToolResultMessage trm) {
            StringBuilder textContent = new StringBuilder();
            for (MessageContent mc : trm.content()) {
                if (mc instanceof TextContent tc) {
                    textContent.append(tc.text());
                }
            }
            map.put("tool_call_id", trm.toolCallId());
            map.put("content", textContent.toString());
        }

        return map;
    }

    private CompletableFuture<AssistantMessage> callUpstream(
            ProviderConfig provider,
            String protocol,
            Map<String, Object> payload,
            ModelRef modelRef,
            AgentEventSink eventSink
    ) {
        return CompletableFuture.supplyAsync(() -> {
            String messageId = UUID.randomUUID().toString();
            StreamAdapter.StreamAccumulator accumulator = new StreamAdapter.StreamAccumulator(messageId, modelRef);

            try {
                // 1. 确保开启流式传输 (SSE)
                payload.put("stream", true);
                ApiKeyConfig apiKeyConfig = ProviderRegistry.toApiKeyConfig(provider);
                String defaultPath = "anthropic".equalsIgnoreCase(protocol) ? "/v1/messages" : "/v1/chat/completions";

                log.info("Calling upstream AI via ai-router-core [{}], protocol [{}], model [{}], path [{}]",
                        provider.providerId(), protocol, modelRef.id(), defaultPath);

                // 2. 利用 ai-router-core 的 UpstreamStreamClient 发起流式调用（内置鉴权头注入、URL 规范化、虚拟线程空闲超时保护）
                try (BufferedReader reader = upstreamStreamClient.stream(apiKeyConfig, defaultPath, payload)) {
                    // 3. 统一消费 SSE：StreamAdapter 委托 DeltaJsonParser 解析（含 message_start usage 兜底补录）
                    streamAdapter.consumeStream(reader, protocol, accumulator, eventSink);
                } catch (UpstreamException e) {
                    int statusCode = e.getUpstreamStatus() != 0 ? e.getUpstreamStatus() : (e.getHttpStatus() != 0 ? e.getHttpStatus() : 502);
                    log.error("Upstream error (HTTP {}): {}", statusCode, e.getMessage());
                    com.claubloom.harness.ai.exception.BloomAiException mappedEx = mapHttpError(statusCode, e.getMessage());
                    if (eventSink != null) {
                        try {
                            eventSink.emit(new com.claubloom.harness.core.event.MessageUpdateEvent(
                                    messageId, 0, "text", "\n\n⚠️ **" + mappedEx.getMessage() + "**\n\n> 💡 **排查建议**: " + mappedEx.getSuggestion() + "\n\n```json\n" + mappedEx.getDetails() + "\n```\n"
                            ));
                        } catch (Exception ex) {
                            log.warn("Failed emitting upstream error event", ex);
                        }
                    }
                    return AssistantMessage.error(
                            messageId,
                            List.of(new TextContent("上游调用失败: " + mappedEx.getMessage() + "\n建议: " + mappedEx.getSuggestion())),
                            modelRef,
                            null,
                            System.currentTimeMillis(),
                            mappedEx.getMessage()
                    );
                }

                AssistantMessage message = accumulator.toAssistantMessage(objectMapper);
                if (message.content().isEmpty() || (message.content().get(0) instanceof TextContent tc && tc.text().isBlank())) {
                    log.warn("Upstream AI response finished with empty content, model: {}", modelRef.id());
                }
                return message;
            } catch (com.claubloom.harness.ai.exception.BloomAiException e) {
                log.error("AI invocation failed with domain error [{}]: {}", e.getErrorCode(), e.getMessage());
                if (eventSink != null) {
                    try {
                        eventSink.emit(new com.claubloom.harness.core.event.MessageUpdateEvent(
                                messageId, 0, "text", "\n\n⚠️ **" + e.getMessage() + "**\n\n> 💡 **建议**: " + e.getSuggestion()
                        ));
                    } catch (Exception ignored) {}
                }
                return AssistantMessage.error(
                        messageId,
                        List.of(new TextContent(e.getMessage() + "\n💡 建议: " + e.getSuggestion())),
                        modelRef,
                        null,
                        System.currentTimeMillis(),
                        e.getMessage()
                );
            } catch (Exception e) {
                log.error("Failed to stream upstream AI response", e);
                if (eventSink != null) {
                    try {
                        eventSink.emit(new com.claubloom.harness.core.event.MessageUpdateEvent(
                                messageId, 0, "text", "\n\n⚠️ **连接上游异常**: " + e.getMessage()
                        ));
                    } catch (Exception ignored) {}
                }
                return AssistantMessage.error(
                        messageId,
                        List.of(new TextContent("Upstream call failed: " + e.getMessage())),
                        modelRef,
                        null,
                        System.currentTimeMillis(),
                        e.getMessage()
                );
            }
        }, Thread::startVirtualThread);
    }

    private com.claubloom.harness.ai.exception.BloomAiException mapHttpError(int statusCode, String errorBody) {
        String friendlyMsg = extractErrorMessage(errorBody, statusCode);
        return switch (statusCode) {
            case 401, 403 -> new com.claubloom.harness.ai.exception.AiExceptions.AuthException(
                    statusCode, "上游鉴权失败 (HTTP " + statusCode + "): " + friendlyMsg, errorBody);
            case 402 -> new com.claubloom.harness.ai.exception.AiExceptions.QuotaExhaustedException(
                    "服务商账户余额已耗尽 (HTTP 402): " + friendlyMsg, errorBody);
            case 429 -> new com.claubloom.harness.ai.exception.AiExceptions.RateLimitException(
                    "触发服务商调用频率或并发限制 (HTTP 429): " + friendlyMsg, errorBody);
            case 400 -> new com.claubloom.harness.ai.exception.AiExceptions.BadRequestException(
                    "请求参数错误或模型不存在 (HTTP 400): " + friendlyMsg, errorBody);
            case 500, 502, 503, 504 -> new com.claubloom.harness.ai.exception.AiExceptions.UpstreamServerException(
                    statusCode, "服务商服务端内部异常 (HTTP " + statusCode + "): " + friendlyMsg, errorBody);
            default -> new com.claubloom.harness.ai.exception.BloomAiException(
                    "AI_UPSTREAM_HTTP_" + statusCode, statusCode, "上游响应异常 (HTTP " + statusCode + "): " + friendlyMsg,
                    "请根据上方返回的错误信息排查服务商状态。", false, errorBody);
        };
    }

    private String extractErrorMessage(String errorBody, int statusCode) {
        if (errorBody == null || errorBody.isBlank()) {
            return "HTTP " + statusCode;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(errorBody);
            if (root.has("error")) {
                com.fasterxml.jackson.databind.JsonNode errNode = root.get("error");
                if (errNode.has("message")) {
                    return errNode.get("message").asText();
                }
            }
        } catch (Exception ignored) {}
        return errorBody.length() > 300 ? errorBody.substring(0, 300) + "..." : errorBody;
    }
}
