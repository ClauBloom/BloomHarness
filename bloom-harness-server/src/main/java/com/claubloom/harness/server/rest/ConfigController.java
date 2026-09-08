package com.claubloom.harness.server.rest;

import com.claubloom.harness.ai.provider.ProviderConfig;
import com.claubloom.harness.ai.provider.ProviderRegistry;
import com.miniapi.router.core.domain.ApiKeyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 模型服务商与 API Key 凭证配置 REST 接口。
 * 提供服务商 CRUD、机密 API Key 脱敏查询、端点连通性探测与模型列表自动拉取，
 * 以及全局通用偏好配置（默认模型、采样温度、系统提示词模板）的读写。
 */
@Slf4j
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ProviderRegistry providerRegistry;

    // 内存中通用的运行时偏好配置缓存（支持扩展至 SQLite 持久化）
    private final Map<String, Object> generalSettings = new ConcurrentHashMap<>(Map.of(
            "defaultProvider", "",
            "defaultModel", "",
            "temperature", 0.7,
            "maxTokens", 4096,
            "systemPrompt", "You are an expert AI software engineer pair programming in BloomHarness."
    ));

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 查询所有已注册的 AI 模型服务商配置列表（机密 API Key 实施单向脱敏）。
     */
    @GetMapping("/providers")
    public List<Map<String, Object>> listProviders(@RequestParam(value = "reveal", defaultValue = "false") boolean reveal) {
        return providerRegistry.list().stream().map(p -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("providerId", p.providerId());
            map.put("name", p.name());
            map.put("baseUrl", p.baseUrl());
            map.put("protocol", p.protocol());
            map.put("models", p.models());

            String key = p.apiKey();
            boolean isConfigured = key != null && !key.isBlank();
            map.put("isConfigured", isConfigured);

            if (reveal || !isConfigured) {
                map.put("apiKey", key != null ? key : "");
            } else if (key.length() > 8) {
                map.put("apiKey", key.substring(0, 4) + "••••••••" + key.substring(key.length() - 4));
            } else {
                map.put("apiKey", "••••••••");
            }
            return map;
        }).toList();
    }

    /**
     * 添加或更新 AI 供应商配置。
     * 与 ai-router-core 的 ApiKeyConfig 领域模型集成。
     */
    @PostMapping("/providers")
    public ResponseEntity<Map<String, Object>> saveProvider(@RequestBody Map<String, Object> body) {
        String providerId = (String) body.get("providerId");
        String name = (String) body.getOrDefault("name", providerId);
        String baseUrl = (String) body.get("baseUrl");
        String apiKey = (String) body.get("apiKey");
        String protocol = (String) body.getOrDefault("protocol", "openai");

        @SuppressWarnings("unchecked")
        List<String> models = (List<String>) body.getOrDefault("models", List.of());
        if (models == null) models = List.of();

        if (providerId == null || providerId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "providerId is required"));
        }

        // 若前端提交的是掩码密钥或为空，保留此前已持久化的真实密钥
        if (apiKey != null && apiKey.contains("••••")) {
            Optional<ProviderConfig> existing = providerRegistry.find(providerId);
            if (existing.isPresent()) {
                apiKey = existing.get().apiKey();
            }
        }

        // 转换为 ai-router-core 的 ApiKeyConfig 领域模型以实现底层路由耦合
        ApiKeyConfig apiKeyConfig = new ApiKeyConfig();
        apiKeyConfig.setName(name);
        apiKeyConfig.setProvider(providerId.toLowerCase());
        apiKeyConfig.setBaseUrl(baseUrl);
        apiKeyConfig.setApiKey(apiKey);
        apiKeyConfig.setProtocol(protocol);

        // 转换并在服务商注册表中完成注册
        ProviderConfig providerConfig = new ProviderConfig(
                providerId.toLowerCase(),
                name,
                baseUrl != null ? baseUrl.strip() : "",
                apiKey != null ? apiKey.strip() : "",
                protocol,
                models
        );
        providerRegistry.register(providerConfig);

        log.info("Saved provider configuration for {} with baseUrl: {}", providerId, baseUrl);
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "providerId", providerId,
                "name", name,
                "baseUrl", baseUrl != null ? baseUrl : "",
                "isConfigured", apiKey != null && !apiKey.isBlank()
        ));
    }

    /**
     * 测试与目标服务商上游 API 端点的网络连通性并测量往返延迟。
     */
    @PostMapping("/providers/test")
    public ResponseEntity<Map<String, Object>> testProvider(@RequestBody Map<String, Object> body) {
        String providerId = (String) body.get("providerId");
        String baseUrl = (String) body.get("baseUrl");
        String apiKey = (String) body.get("apiKey");

        // 若 apiKey 已被掩码，则查找现有的配置
        if (apiKey != null && apiKey.contains("••••") && providerId != null) {
            Optional<ProviderConfig> existing = providerRegistry.find(providerId);
            if (existing.isPresent()) {
                apiKey = existing.get().apiKey();
            }
        }

        if (baseUrl == null || baseUrl.isBlank()) {
            return ResponseEntity.ok(Map.of(
                    "ok", false,
                    "message", "Base URL 为空，无法测试连接"
            ));
        }

        long start = System.currentTimeMillis();
        try {
            // 去除末尾斜杠，规范化 URL
            String target = baseUrl.strip();
            if (target.endsWith("/")) {
                target = target.substring(0, target.length() - 1);
            }

            // 探测目标：先尝试 /models（OpenAI/Ollama 格式），否则直接探测目标地址
            String probeUrl = target.endsWith("/v1") ? target + "/models" : target;
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(probeUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET();

            if (apiKey != null && !apiKey.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            int code = response.statusCode();
            if (code >= 200 && code < 300) {
                return ResponseEntity.ok(Map.of(
                        "ok", true,
                        "latencyMs", latency,
                        "statusCode", code,
                        "message", "连接成功 (" + latency + "ms)"
                ));
            } else if (code == 401 || code == 403) {
                return ResponseEntity.ok(Map.of(
                        "ok", false,
                        "latencyMs", latency,
                        "statusCode", code,
                        "message", "鉴权失败 (" + code + "): 请检查 API Key 是否正确"
                ));
            } else if (code == 404) {
                // 部分供应商没有 /models，但主机可达
                return ResponseEntity.ok(Map.of(
                        "ok", true,
                        "latencyMs", latency,
                        "statusCode", code,
                        "message", "端点可达 (" + latency + "ms, 返回 404)"
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "ok", false,
                        "latencyMs", latency,
                        "statusCode", code,
                        "message", "上游响应异常: HTTP " + code
                ));
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("Failed to test provider connection to {}: {}", baseUrl, e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "ok", false,
                    "latencyMs", latency,
                    "message", "连接失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
            ));
        }
    }

    /**
     * 从服务商上游的 /models 或 /api/tags 端点一键自动拉取可用模型列表。
     */
    @PostMapping("/providers/fetch-models")
    public ResponseEntity<Map<String, Object>> fetchModels(@RequestBody Map<String, Object> body) {
        String providerId = (String) body.get("providerId");
        String baseUrl = (String) body.get("baseUrl");
        String apiKey = (String) body.get("apiKey");

        // 若已掩码则保留现有密钥
        if (apiKey != null && apiKey.contains("••••") && providerId != null) {
            Optional<ProviderConfig> existing = providerRegistry.find(providerId);
            if (existing.isPresent()) {
                apiKey = existing.get().apiKey();
            }
        }

        if (baseUrl == null || baseUrl.isBlank()) {
            return ResponseEntity.ok(Map.of(
                    "ok", false,
                    "message", "请先填写 Base URL 才能获取模型列表"
            ));
        }

        try {
            String target = baseUrl.strip();
            while (target.endsWith("/")) {
                target = target.substring(0, target.length() - 1);
            }
            if (target.endsWith("/chat/completions")) {
                target = target.substring(0, target.length() - "/chat/completions".length());
            }

            // 确定候选端点：/models 或 /v1/models 或 /api/tags
            List<String> candidateUrls = new ArrayList<>();
            if (target.endsWith("/v1")) {
                candidateUrls.add(target + "/models");
            } else {
                candidateUrls.add(target + "/v1/models");
                candidateUrls.add(target + "/models");
                candidateUrls.add(target + "/api/tags"); // Ollama
            }

            String lastError = null;
            List<String> modelList = new ArrayList<>();

            for (String probeUrl : candidateUrls) {
                try {
                    HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                            .uri(URI.create(probeUrl))
                            .timeout(Duration.ofSeconds(10))
                            .GET();

                    if (apiKey != null && !apiKey.isBlank()) {
                        reqBuilder.header("Authorization", "Bearer " + apiKey.strip());
                    }

                    HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
                    int code = response.statusCode();

                    if (code >= 200 && code < 300) {
                        modelList = parseModelsFromJson(response.body());
                        if (!modelList.isEmpty()) {
                            log.info("Successfully fetched {} models from {}", modelList.size(), probeUrl);
                            break;
                        }
                    } else if (code == 401 || code == 403) {
                        return ResponseEntity.ok(Map.of(
                                "ok", false,
                                "message", "鉴权失败 (HTTP " + code + "): 请确认 API Key 是否正确"
                        ));
                    } else {
                        lastError = "HTTP " + code;
                    }
                } catch (Exception ex) {
                    lastError = ex.getMessage();
                }
            }

            if (!modelList.isEmpty()) {
                // 按字母顺序对模型排序
                Collections.sort(modelList);
                return ResponseEntity.ok(Map.of(
                        "ok", true,
                        "models", modelList,
                        "count", modelList.size(),
                        "message", "成功获取到 " + modelList.size() + " 个可用模型"
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "ok", false,
                        "message", "未能从服务商端点自动拉取模型列表 (" + (lastError != null ? lastError : "端点未返回模型数据") + ")，可手动在下方添加模型名称。"
                ));
            }
        } catch (Exception e) {
            log.error("Failed to fetch models from {}: {}", baseUrl, e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "ok", false,
                    "message", "获取模型列表失败: " + e.getMessage()
            ));
        }
    }

    private List<String> parseModelsFromJson(String jsonBody) {
        Set<String> result = new LinkedHashSet<>();
        if (jsonBody == null || jsonBody.isBlank()) return List.of();

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(jsonBody);

            // 1. OpenAI / DeepSeek 格式：{"data": [{"id": "deepseek-chat"}, ...]}
            if (root.has("data") && root.get("data").isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode item : root.get("data")) {
                    if (item.has("id")) {
                        result.add(item.get("id").asText());
                    }
                }
            }

            // 2. Ollama 格式：{"models": [{"name": "qwen2.5:latest"}, ...]}
            if (root.has("models") && root.get("models").isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode item : root.get("models")) {
                    if (item.has("name")) {
                        result.add(item.get("name").asText());
                    } else if (item.has("id")) {
                        result.add(item.get("id").asText());
                    }
                }
            }

            // 3. 兜底方案：根数组
            if (root.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode item : root) {
                    if (item.has("id")) {
                        result.add(item.get("id").asText());
                    } else if (item.isTextual()) {
                        result.add(item.asText());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing models JSON response: {}", e.getMessage());
        }

        return new ArrayList<>(result);
    }

    /**
     * 删除指定服务商配置。
     */
    @DeleteMapping("/providers/{providerId}")
    public ResponseEntity<Map<String, Object>> deleteProvider(@PathVariable String providerId) {
        providerRegistry.remove(providerId);
        return ResponseEntity.ok(Map.of("ok", true, "removed", providerId));
    }

    /**
     * 获取全局通用偏好配置（默认模型、采样温度、最大生成 Token 数等）。
     */
    @GetMapping("/general")
    public Map<String, Object> getGeneralSettings() {
        return new LinkedHashMap<>(generalSettings);
    }

    /**
     * 更新全局通用偏好配置。
     */
    @PostMapping("/general")
    public ResponseEntity<Map<String, Object>> updateGeneralSettings(@RequestBody Map<String, Object> body) {
        body.forEach((k, v) -> {
            if (v != null) {
                generalSettings.put(k, v);
            }
        });
        return ResponseEntity.ok(Map.of("ok", true, "settings", generalSettings));
    }
}
