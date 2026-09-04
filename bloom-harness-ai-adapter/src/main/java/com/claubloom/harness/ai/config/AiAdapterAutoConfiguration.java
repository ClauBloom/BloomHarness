package com.claubloom.harness.ai.config;

import com.claubloom.harness.ai.adapter.AiModelAdapter;
import com.claubloom.harness.ai.adapter.StreamAdapter;
import com.claubloom.harness.ai.provider.ProviderRegistry;
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

import java.util.List;

/**
 * bloom-harness-ai-adapter 模块的 Spring Boot 自动装配类。
 */
@AutoConfiguration
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
    @ConditionalOnMissingBean
    public StreamAdapter streamAdapter() {
        return new StreamAdapter();
    }

    @Bean
    @ConditionalOnMissingBean
    public AiModelAdapter aiModelAdapter(
            ProtocolRegistry protocolRegistry,
            ProviderRegistry providerRegistry,
            StreamAdapter streamAdapter
    ) {
        return new AiModelAdapter(protocolRegistry, providerRegistry, streamAdapter);
    }
}
