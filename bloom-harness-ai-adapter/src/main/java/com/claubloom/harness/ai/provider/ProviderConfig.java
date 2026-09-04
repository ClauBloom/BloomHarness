package com.claubloom.harness.ai.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * AI 上游模型服务商的配置描述对象。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderConfig(
        @JsonProperty(value = "providerId", required = true)
        String providerId,
        @JsonProperty(value = "name", required = true)
        String name,
        @JsonProperty("baseUrl")
        String baseUrl,
        @JsonProperty("apiKey")
        String apiKey,
        @JsonProperty(value = "protocol", defaultValue = "openai")
        String protocol,
        @JsonProperty("supportedModels")
        List<String> supportedModels
) {
    public static ProviderConfig of(String providerId, String name, String baseUrl, String apiKey, String protocol) {
        return new ProviderConfig(providerId, name, baseUrl, apiKey, protocol, List.of());
    }

    public List<String> models() {
        return supportedModels != null ? supportedModels : List.of();
    }
}
