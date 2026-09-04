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
import com.miniapi.router.core.protocol.ProtocolRegistry;
import com.miniapi.router.core.protocol.UnifiedRequest;
import com.miniapi.router.core.protocol.converter.RequestConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * AiModelAdapter translates Agent messages to UnifiedRequest and invokes upstream APIs via ai-router-core.
 * Implements core LlmCaller interface so it can directly power AgentLoop.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelAdapter implements LlmCaller {

    private final ProtocolRegistry protocolRegistry;
    private final ProviderRegistry providerRegistry;
    private final StreamAdapter streamAdapter;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

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

        // 显式保障 upstreamPayload 携带标准 OpenAI tools 结构
        if (unifiedRequest.getTools() != null && !unifiedRequest.getTools().isEmpty()) {
            upstreamPayload.put("tools", unifiedRequest.getTools());
        }

        return callUpstream(provider, upstreamPayload, modelRef, eventSink);
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
                    toolCalls.add(Map.of(
                            "id", tc.toolCallId(),
                            "type", "function",
                            "function", Map.of(
                                    "name", tc.toolName(),
                                    "arguments", tc.input() != null ? tc.input().toString() : "{}"
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
            Map<String, Object> payload,
            ModelRef modelRef,
            AgentEventSink eventSink
    ) {
        return CompletableFuture.supplyAsync(() -> {
            String messageId = UUID.randomUUID().toString();
            StreamAdapter.StreamAccumulator accumulator = new StreamAdapter.StreamAccumulator(messageId, modelRef);

            try {
                // 1. 规范化并组装上游完整 URL
                String url = resolveUpstreamUrl(provider);

                // 2. 确保开启流式传输 (SSE)
                payload.put("stream", true);
                String jsonBody = objectMapper.writeValueAsString(payload);
                log.info("Calling upstream AI provider [{}], model [{}], endpoint: {}", provider.providerId(), modelRef.id(), url);

                // 3. 构建多级超时控制的 HTTP 请求 (连接超时 8s, 首包读取超时 25s)
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(25)) // 首包及单次读取超时
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

                if (provider.apiKey() != null && !provider.apiKey().isBlank()) {
                    reqBuilder.header("Authorization", "Bearer " + provider.apiKey().strip());
                }

                HttpResponse<java.io.InputStream> response;
                try {
                    response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
                } catch (java.net.http.HttpConnectTimeoutException e) {
                    throw new com.claubloom.harness.ai.exception.AiExceptions.TimeoutException(
                            "connect", "连接上游端点超时 (超过 8s)", e.getMessage());
                } catch (java.net.http.HttpTimeoutException e) {
                    throw new com.claubloom.harness.ai.exception.AiExceptions.TimeoutException(
                            "first_byte", "等待上游首包响应超时 (超过 25s)", e.getMessage());
                } catch (java.net.ConnectException | java.nio.channels.UnresolvedAddressException e) {
                    throw new com.claubloom.harness.ai.exception.AiExceptions.NetworkException(
                            "无法连接至服务商端点: " + e.getClass().getSimpleName(), e.getMessage(), e);
                }

                int statusCode = response.statusCode();

                // 4. 精准映射并抛出非 200 HTTP 领域异常
                if (statusCode < 200 || statusCode >= 300) {
                    String errorBody;
                    try (var is = response.body()) {
                        errorBody = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    }
                    log.error("Upstream AI returned HTTP {}: {}", statusCode, errorBody);
                    com.claubloom.harness.ai.exception.BloomAiException mappedEx = mapHttpError(statusCode, errorBody);

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

                // 5. 解析 200 OK 流式 SSE 数据
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(response.body()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        var chunk = streamAdapter.parseOpenAiChunk(line);
                        if (chunk != null) {
                            accumulator.appendChunk(chunk, eventSink);
                        }
                    }
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

    /**
     * 智能规范化 Base URL，避免双重路径（如 /chat/completions/chat/completions）
     */
    private String resolveUpstreamUrl(ProviderConfig provider) {
        String baseUrl = provider.baseUrl() != null ? provider.baseUrl().strip() : "";
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        if (baseUrl.isBlank()) {
            return "https://api.openai.com/v1/chat/completions";
        }

        if (baseUrl.endsWith("/chat/completions")) {
            return baseUrl;
        }

        return baseUrl + "/chat/completions";
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
