package com.claubloom.harness.ai.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.miniapi.router.core.domain.ApiKeyConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 管理 AI 上游模型服务商配置的线程安全注册表。
 * 支持将服务商配置原子持久化至本地 JSON 文件 (./bloom-providers.json)，服务重启不丢失。
 * 与 ai-router-core 路由引擎的 ApiKeyConfig 领域模型保持深度耦合。
 */
@Slf4j
@Component
public class ProviderRegistry {

    private static final String DEFAULT_STORAGE_FILE = "./bloom-providers.json";
    private static final String BACKUP_STORAGE_DIR = System.getProperty("user.home") + "/.bloom";

    private final Map<String, ProviderConfig> providers = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Path storagePath = resolveStoragePath();

    public ProviderRegistry() {
        // 空参内存构造方法；持久化配置在 @PostConstruct 生命周期中加载
    }

    @PostConstruct
    public synchronized void init() {
        loadFromDisk();
    }

    private Path resolveStoragePath() {
        try {
            Path localPath = Path.of(DEFAULT_STORAGE_FILE).toAbsolutePath();
            File parentDir = localPath.getParent() != null ? localPath.getParent().toFile() : null;
            if (parentDir != null && parentDir.canWrite()) {
                return localPath;
            }
        } catch (Exception e) {
            log.warn("Local storage path check failed: {}", e.getMessage());
        }

        try {
            Path homeDir = Path.of(BACKUP_STORAGE_DIR);
            if (!Files.exists(homeDir)) {
                Files.createDirectories(homeDir);
            }
            return homeDir.resolve("providers.json");
        } catch (Exception e) {
            log.warn("Home directory fallback creation failed, using current directory: {}", e.getMessage());
            return Path.of(DEFAULT_STORAGE_FILE).toAbsolutePath();
        }
    }

    public synchronized void register(ProviderConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(config.providerId(), "providerId must not be null");
        providers.put(config.providerId().toLowerCase(), config);
        log.info("Registered/Updated AI provider: {}", config.providerId());
        persistToDisk();
    }

    public synchronized void remove(String providerId) {
        if (providerId != null) {
            providers.remove(providerId.toLowerCase());
            log.info("Removed AI provider: {}", providerId);
            persistToDisk();
        }
    }

    public Optional<ProviderConfig> find(String providerId) {
        if (providerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.get(providerId.toLowerCase()));
    }

    public Collection<ProviderConfig> list() {
        return Collections.unmodifiableCollection(providers.values());
    }

    /**
     * 将当前内存中的服务商配置持久化落盘至本地文件。
     */
    private synchronized void persistToDisk() {
        try {
            List<ProviderConfig> list = new ArrayList<>(providers.values());
            Path tempPath = Path.of(storagePath.toString() + ".tmp");
            objectMapper.writeValue(tempPath.toFile(), list);
            Files.move(tempPath, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("Persisted {} provider configurations to {}", list.size(), storagePath);
        } catch (IOException e) {
            log.error("Failed to persist providers to {}: {}", storagePath, e.getMessage(), e);
        }
    }

    /**
     * 从本地持久化文件中加载服务商配置至内存注册表。
     */
    private synchronized void loadFromDisk() {
        if (!Files.exists(storagePath)) {
            log.info("No existing provider persistence file found at {}. Initializing empty registry.", storagePath);
            return;
        }

        try {
            List<ProviderConfig> loaded = objectMapper.readValue(
                    storagePath.toFile(),
                    new TypeReference<List<ProviderConfig>>() {}
            );
            if (loaded != null) {
                providers.clear();
                for (ProviderConfig p : loaded) {
                    if (p.providerId() != null) {
                        providers.put(p.providerId().toLowerCase(), p);
                    }
                }
                log.info("Successfully loaded {} provider configurations from persistence file: {}", providers.size(), storagePath);
            }
        } catch (Exception e) {
            log.error("Failed to load provider configurations from {}: {}", storagePath, e.getMessage(), e);
        }
    }

    /**
     * 将 ai-router-core 的 ApiKeyConfig 领域对象转换为 BloomHarness 的 ProviderConfig 服务商配置。
     */
    public static ProviderConfig fromApiKeyConfig(ApiKeyConfig apiKeyConfig) {
        if (apiKeyConfig == null) return null;
        List<String> models = apiKeyConfig.getModelMapping() != null
                ? new ArrayList<>(apiKeyConfig.getModelMapping().keySet())
                : List.of();
        return new ProviderConfig(
                apiKeyConfig.getProvider() != null ? apiKeyConfig.getProvider().toLowerCase() : "custom",
                apiKeyConfig.getName() != null ? apiKeyConfig.getName() : apiKeyConfig.getProvider(),
                apiKeyConfig.getBaseUrl(),
                apiKeyConfig.getApiKey(),
                apiKeyConfig.getProtocol() != null ? apiKeyConfig.getProtocol().toLowerCase() : "openai",
                models
        );
    }

    /**
     * 将 BloomHarness 的 ProviderConfig 服务商配置转换为 ai-router-core 的 ApiKeyConfig 领域对象。
     */
    public static ApiKeyConfig toApiKeyConfig(ProviderConfig config) {
        if (config == null) return null;
        ApiKeyConfig keyConfig = new ApiKeyConfig();
        keyConfig.setName(config.name());
        keyConfig.setProvider(config.providerId());
        keyConfig.setProtocol(config.protocol());
        keyConfig.setBaseUrl(config.baseUrl());
        keyConfig.setApiKey(config.apiKey());
        keyConfig.setStatus(1); // 启用
        if (config.models() != null && !config.models().isEmpty()) {
            Map<String, String> mapping = config.models().stream()
                    .collect(Collectors.toMap(m -> m, m -> m, (a, b) -> a, LinkedHashMap::new));
            keyConfig.setModelMapping(mapping);
        }
        return keyConfig;
    }
}
