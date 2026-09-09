package com.claubloom.harness.ai.config;

import com.claubloom.harness.ai.adapter.SpringAiModelAdapter;
import com.claubloom.harness.ai.provider.ProviderRegistry;
import com.claubloom.harness.ai.router.RouterEngineConfiguration;
import com.claubloom.harness.core.loop.LlmCaller;
import com.miniapi.router.core.protocol.ProtocolRegistry;
import com.miniapi.router.core.protocol.ReasoningContentCache;
import com.miniapi.router.core.protocol.converter.RequestConverter;
import com.miniapi.router.core.protocol.converter.ResponseConverter;
import com.miniapi.router.core.protocol.converter.StreamConverter;
import com.miniapi.router.core.protocol.converter.anthropic.AnthropicRequestConverter;
import com.miniapi.router.core.protocol.converter.anthropic.AnthropicResponseConverter;
import com.miniapi.router.core.protocol.converter.anthropic.AnthropicStreamConverter;
import com.miniapi.router.core.protocol.converter.openai.OpenAIRequestConverter;
import com.miniapi.router.core.protocol.converter.openai.OpenAIResponseConverter;
import com.miniapi.router.core.protocol.converter.openai.OpenAIStreamConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;

/**
 * bloom-harness-ai-adapter 模块的 Spring Boot 自动装配类。
 * <p>
 * 模型调用链路已整体迁移至 ai-router-core Spring AI 桥接层：
 * 本类仅装配协议转换器（openai/anthropic）、ProviderRegistry 与 {@link SpringAiModelAdapter}；
 * 路由引擎（RoutePipeline/DefaultRouterCore/ChatModelRouter）由
 * {@link RouterEngineConfiguration} 装配。
 */
@AutoConfiguration
@Import(RouterEngineConfiguration.class)
@EnableConfigurationProperties(AiProperties.class)
public class AiAdapterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ReasoningContentCache reasoningContentCache() {
        return new ReasoningContentCache();
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenAIRequestConverter openAIRequestConverter(ReasoningContentCache reasoningContentCache) {
        return new OpenAIRequestConverter(reasoningContentCache);
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenAIResponseConverter openAIResponseConverter() {
        return new OpenAIResponseConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenAIStreamConverter openAIStreamConverter() {
        return new OpenAIStreamConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public AnthropicRequestConverter anthropicRequestConverter() {
        return new AnthropicRequestConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public AnthropicResponseConverter anthropicResponseConverter() {
        return new AnthropicResponseConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public AnthropicStreamConverter anthropicStreamConverter() {
        return new AnthropicStreamConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProtocolRegistry protocolRegistry(
            List<RequestConverter> requestConverters,
            List<ResponseConverter> responseConverters,
            List<StreamConverter> streamConverters
    ) {
        return new ProtocolRegistry(requestConverters, responseConverters, streamConverters);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProviderRegistry providerRegistry() {
        return new ProviderRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(LlmCaller.class)
    public SpringAiModelAdapter springAiModelAdapter(
            com.miniapi.router.core.springai.ChatModelRouter chatModelRouter,
            ProviderRegistry providerRegistry
    ) {
        return new SpringAiModelAdapter(chatModelRouter, providerRegistry);
    }
}
