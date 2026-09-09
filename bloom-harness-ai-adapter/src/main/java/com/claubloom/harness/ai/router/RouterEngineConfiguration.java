package com.claubloom.harness.ai.router;

import com.claubloom.harness.ai.provider.ProviderConfig;
import com.claubloom.harness.ai.provider.ProviderRegistry;
import com.miniapi.router.core.api.DefaultRouterCore;
import com.miniapi.router.core.api.RouterCore;
import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.domain.IntentConfig;
import com.miniapi.router.core.domain.ModelConfig;
import com.miniapi.router.core.domain.RouteRule;
import com.miniapi.router.core.intent.IntentEvaluator;
import com.miniapi.router.core.intent.PromptTemplate;
import com.miniapi.router.core.protocol.ProtocolRegistry;
import com.miniapi.router.core.protocol.ReasoningContentCache;
import com.miniapi.router.core.protocol.converter.openai.OpenAIRequestConverter;
import com.miniapi.router.core.protocol.converter.springai.SpringAiPromptConverter;
import com.miniapi.router.core.protocol.converter.springai.SpringAiResponseConverter;
import com.miniapi.router.core.protocol.converter.springai.SpringAiStreamConverter;
import com.miniapi.router.core.routing.FailureTracker;
import com.miniapi.router.core.routing.RoutePipeline;
import com.miniapi.router.core.routing.SessionRouteMemory;
import com.miniapi.router.core.routing.UpstreamCooldownTracker;
import com.miniapi.router.core.routing.strategy.LeastConnStrategy;
import com.miniapi.router.core.routing.strategy.PriorityStrategy;
import com.miniapi.router.core.routing.strategy.RoundRobinStrategy;
import com.miniapi.router.core.routing.strategy.RouteStrategyRegistry;
import com.miniapi.router.core.routing.strategy.WeightStrategy;
import com.miniapi.router.core.spi.ApiKeyConfigRepository;
import com.miniapi.router.core.spi.IntentCatalogProvider;
import com.miniapi.router.core.spi.ModelConfigRepository;
import com.miniapi.router.core.spi.RouteRuleRepository;
import com.miniapi.router.core.spi.UpstreamClient;
import com.miniapi.router.core.springai.ChatModelRouter;
import com.miniapi.router.core.springai.DefaultChatModelRouter;
import com.miniapi.router.core.streaming.StreamProxy;
import com.miniapi.router.core.streaming.UpstreamResponseParser;
import com.miniapi.router.core.streaming.UpstreamStreamClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * ai-router-core 路由引擎装配配置。
 * <p>
 * 将 BloomHarness 的 {@link ProviderRegistry}（bloom-providers.json）适配为引擎的三个 SPI 仓储，
 * 并装配 {@link RoutePipeline} / {@link DefaultRouterCore} / Spring AI 转换器 /
 * {@link ChatModelRouter} 全家桶。此后模型调用路由完全借助 ai-router-core 引擎：
 * <ul>
 *   <li>按模型名跨供应商路由（modelMapping 匹配）+ 策略选择（round_robin 等）</li>
 *   <li>上游协议自动转换（openai ↔ anthropic）</li>
 *   <li>故障回退链与 Key 冷却追踪</li>
 *   <li>Agent 会话路由记忆（agentIdentity 接通）</li>
 * </ul>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class RouterEngineConfiguration {

    /* ---------- SPI 适配：把 ProviderRegistry 映射为路由引擎仓储 ---------- */

    @Bean
    @ConditionalOnMissingBean(ApiKeyConfigRepository.class)
    public ApiKeyConfigRepository bloomApiKeyConfigRepository(ProviderRegistry registry) {
        return new BloomApiKeyConfigRepository(registry);
    }

    @Bean
    @ConditionalOnMissingBean(RouteRuleRepository.class)
    public RouteRuleRepository bloomRouteRuleRepository() {
        return new BloomRouteRuleRepository();
    }

    @Bean
    @ConditionalOnMissingBean(ModelConfigRepository.class)
    public ModelConfigRepository bloomModelConfigRepository() {
        return new BloomModelConfigRepository();
    }

    @Bean
    @ConditionalOnMissingBean(IntentCatalogProvider.class)
    public IntentCatalogProvider bloomIntentCatalogProvider() {
        // catch-all 规则非 intent 类型，意图目录不会被消费；提供空目录实现
        return new IntentCatalogProvider() {
            @Override
            public List<IntentConfig> findAll(Long tenantId) {
                return List.of();
            }

            @Override
            public IntentConfig findByLabel(Long tenantId, String label) {
                return null;
            }

            @Override
            public IntentConfig findDefault(Long tenantId) {
                return null;
            }
        };
    }

    /* ---------- 引擎协作者 ---------- */

    @Bean
    @ConditionalOnMissingBean
    public UpstreamClient upstreamClient() {
        return new UpstreamStreamClient();
    }

    @Bean
    @ConditionalOnMissingBean
    public UpstreamCooldownTracker upstreamCooldownTracker() {
        return new UpstreamCooldownTracker();
    }

    @Bean
    @ConditionalOnMissingBean
    public FailureTracker failureTracker() {
        return new FailureTracker();
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionRouteMemory sessionRouteMemory() {
        return new SessionRouteMemory();
    }

    @Bean
    @ConditionalOnMissingBean
    public UpstreamResponseParser upstreamResponseParser() {
        return new UpstreamResponseParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public RouteStrategyRegistry routeStrategyRegistry() {
        return new RouteStrategyRegistry(List.of(
                new WeightStrategy(), new RoundRobinStrategy(),
                new PriorityStrategy(), new LeastConnStrategy()));
    }

    @Bean
    @ConditionalOnMissingBean
    public PromptTemplate intentPromptTemplate() {
        return new PromptTemplate();
    }

    @Bean
    @ConditionalOnMissingBean
    public IntentEvaluator intentEvaluator(UpstreamClient upstreamClient,
                                           PromptTemplate promptTemplate,
                                           IntentCatalogProvider intentCatalogProvider,
                                           ModelConfigRepository modelConfigRepository) {
        return new IntentEvaluator(upstreamClient, promptTemplate, intentCatalogProvider, modelConfigRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public RoutePipeline routePipeline(RouteRuleRepository routeRuleRepository,
                                       ApiKeyConfigRepository apiKeyConfigRepository,
                                       IntentEvaluator intentEvaluator,
                                       IntentCatalogProvider intentCatalogProvider,
                                       FailureTracker failureTracker,
                                       SessionRouteMemory sessionRouteMemory,
                                       ModelConfigRepository modelConfigRepository,
                                       RouteStrategyRegistry strategyRegistry,
                                       UpstreamCooldownTracker cooldownTracker) {
        return new RoutePipeline(routeRuleRepository, apiKeyConfigRepository, intentEvaluator,
                intentCatalogProvider, failureTracker, sessionRouteMemory, modelConfigRepository,
                strategyRegistry, cooldownTracker);
    }

    @Bean
    @ConditionalOnMissingBean
    public StreamProxy streamProxy(UpstreamClient upstreamClient,
                                   ProtocolRegistry protocolRegistry,
                                   ReasoningContentCache reasoningContentCache,
                                   UpstreamResponseParser responseParser,
                                   UpstreamCooldownTracker cooldownTracker) {
        return new StreamProxy(upstreamClient, protocolRegistry, reasoningContentCache,
                responseParser, cooldownTracker);
    }

    @Bean
    @ConditionalOnMissingBean(RouterCore.class)
    public RouterCore routerCore(RoutePipeline routePipeline, StreamProxy streamProxy,
                                 ProtocolRegistry protocolRegistry) {
        return new DefaultRouterCore(routePipeline, streamProxy, protocolRegistry);
    }

    /* ---------- Spring AI 桥接 ---------- */

    @Bean
    @ConditionalOnMissingBean
    public SpringAiPromptConverter springAiPromptConverter(OpenAIRequestConverter openAIRequestConverter) {
        return new SpringAiPromptConverter(openAIRequestConverter);
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringAiResponseConverter springAiResponseConverter() {
        return new SpringAiResponseConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringAiStreamConverter springAiStreamConverter(SpringAiResponseConverter responseConverter) {
        return new SpringAiStreamConverter(responseConverter);
    }

    @Bean
    @ConditionalOnMissingBean(ChatModelRouter.class)
    public ChatModelRouter chatModelRouter(RouterCore routerCore,
                                           ProtocolRegistry protocolRegistry,
                                           SpringAiPromptConverter promptConverter,
                                           SpringAiResponseConverter responseConverter,
                                           SpringAiStreamConverter streamConverter) {
        ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();
        ToolExecutionEligibilityPredicate predicate =
                new org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate();
        log.info("Assembled ai-router-core ChatModelRouter (routing, protocol conversion, failover enabled)");
        return new DefaultChatModelRouter(routerCore, promptConverter, responseConverter, streamConverter,
                protocolRegistry, staticProvider(toolCallingManager), staticProvider(predicate));
    }

    private static <T> ObjectProvider<T> staticProvider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return value;
            }

            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }
        };
    }

    /* ---------- 内嵌 SPI 实现 ---------- */

    /**
     * 将 ProviderRegistry（bloom-providers.json）适配为路由引擎的 API Key 仓储。
     * 数字 ID 为内存合成（按 providerId 首见顺序分配，进程内稳定）；save/delete 委托回 ProviderRegistry。
     */
    @Slf4j
    static class BloomApiKeyConfigRepository implements ApiKeyConfigRepository {

        private static final long TENANT_ID = 1L;

        private final ProviderRegistry registry;
        private final Map<String, Long> idByProvider = new ConcurrentHashMap<>();
        private final AtomicLong idSequence = new AtomicLong(1);

        BloomApiKeyConfigRepository(ProviderRegistry registry) {
            this.registry = registry;
        }

        private long idOf(String providerId) {
            return idByProvider.computeIfAbsent(
                    providerId == null ? "custom" : providerId.toLowerCase(), k -> idSequence.getAndIncrement());
        }

        private ApiKeyConfig toKeyConfig(ProviderConfig config) {
            ApiKeyConfig key = ProviderRegistry.toApiKeyConfig(config);
            key.setId(idOf(config.providerId()));
            key.setTenantId(TENANT_ID);
            key.setPriority(1);
            return key;
        }

        @Override
        public ApiKeyConfig findById(Long id) {
            return registry.list().stream()
                    .filter(p -> idOf(p.providerId()) == id)
                    .findFirst().map(this::toKeyConfig).orElse(null);
        }

        @Override
        public ApiKeyConfig findByApiKey(String apiKey) {
            return registry.list().stream()
                    .filter(p -> apiKey != null && apiKey.equals(p.apiKey()))
                    .findFirst().map(this::toKeyConfig).orElse(null);
        }

        @Override
        public List<ApiKeyConfig> findByTenantId(Long tenantId) {
            return registry.list().stream().map(this::toKeyConfig).collect(Collectors.toList());
        }

        @Override
        public List<ApiKeyConfig> findByIds(List<Long> ids) {
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }
            return ids.stream().map(this::findById).filter(Objects::nonNull).collect(Collectors.toList());
        }

        @Override
        public ApiKeyConfig save(ApiKeyConfig config) {
            List<String> models = config.getModelMapping() != null
                    ? new java.util.ArrayList<>(config.getModelMapping().keySet())
                    : List.of();
            registry.register(new ProviderConfig(config.getProvider(), config.getName(), config.getBaseUrl(),
                    config.getApiKey(), config.getProtocol(), models));
            return registry.find(config.getProvider()).map(this::toKeyConfig).orElse(config);
        }

        @Override
        public void update(ApiKeyConfig config) {
            save(config);
        }

        @Override
        public void delete(Long id, Long tenantId) {
            Optional.ofNullable(findById(id)).ifPresent(k -> registry.remove(k.getProvider()));
        }

        @Override
        public void updateStatus(Long id, Long tenantId, int status) {
            // 内存态：BloomHarness 的启用/禁用由 ProviderRegistry 层管理
        }

        @Override
        public void updateHealthStatus(Long id, String healthStatus) {
            // 内存态：健康状态由引擎冷却追踪器管理
        }
    }

    /**
     * 单条 catch-all 通配路由规则：所有模型放行、round_robin 选 Key、
     * 开启故障回退链（maxFallback=3），由引擎执行策略。
     */
    static class BloomRouteRuleRepository implements RouteRuleRepository {

        static final long CATCH_ALL_ID = 1L;

        private RouteRule catchAll() {
            RouteRule rule = new RouteRule();
            rule.setId(CATCH_ALL_ID);
            rule.setTenantId(1L);
            rule.setRuleName("bloom-catch-all");
            rule.setMatchType("model");
            rule.setMatchPattern("*");
            rule.setStrategy("round_robin");
            rule.setFallbackEnabled(true);
            rule.setMaxFallback(3);
            rule.setPriority(0);
            rule.setEnabled(true);
            rule.setDescription("BloomHarness 默认全量放行规则");
            return rule;
        }

        @Override
        public RouteRule findById(Long id) {
            return id != null && id == CATCH_ALL_ID ? catchAll() : null;
        }

        @Override
        public List<RouteRule> findByTenantId(Long tenantId) {
            return List.of(catchAll());
        }

        @Override
        public List<RouteRule> findEnabledRules(Long tenantId) {
            return List.of(catchAll());
        }

        @Override
        public RouteRule save(RouteRule rule) {
            return catchAll();
        }

        @Override
        public void update(RouteRule rule) {
        }

        @Override
        public void delete(Long id, Long tenantId) {
        }

        @Override
        public void updateEnabled(Long id, Long tenantId, boolean enabled) {
        }
    }

    /**
     * 空模型配置仓储：BloomHarness 不使用 model_config 直连与意图模型候选，统一走路由规则。
     */
    static class BloomModelConfigRepository implements ModelConfigRepository {

        @Override
        public List<ModelConfig> findByTenantId(Long tenantId) {
            return List.of();
        }

        @Override
        public ModelConfig findByDisplayName(Long tenantId, String displayName) {
            return null;
        }

        @Override
        public List<ModelConfig> findByApiKeyId(Long apiKeyId) {
            return List.of();
        }

        @Override
        public void save(ModelConfig model) {
        }

        @Override
        public void saveAll(List<ModelConfig> models) {
        }

        @Override
        public void deleteByApiKeyId(Long apiKeyId) {
        }

        @Override
        public void deleteById(Long id) {
        }
    }
}
