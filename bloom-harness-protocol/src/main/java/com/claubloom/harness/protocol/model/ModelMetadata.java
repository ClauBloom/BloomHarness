package com.claubloom.harness.protocol.model;

import com.claubloom.harness.protocol.session.ThinkingLevel;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Metadata definition of an AI model supported by the system.
 */
public record ModelMetadata(
        @JsonProperty(value = "provider", required = true)
        String provider,
        @JsonProperty(value = "id", required = true)
        String id,
        @JsonProperty(value = "name", required = true)
        String name,
        @JsonProperty(value = "api", required = true)
        String api,
        @JsonProperty(value = "reasoning", defaultValue = "false")
        boolean reasoning,
        @JsonProperty(value = "input", required = true)
        List<String> input,
        @JsonProperty(value = "contextWindow", required = true)
        int contextWindow,
        @JsonProperty(value = "maxTokens", required = true)
        int maxTokens,
        @JsonProperty(value = "cost", required = true)
        ModelCost cost,
        @JsonProperty(value = "supportedThinkingLevels", required = true)
        List<ThinkingLevel> supportedThinkingLevels,
        @JsonProperty(value = "authenticated", defaultValue = "true")
        boolean authenticated
) {
}
