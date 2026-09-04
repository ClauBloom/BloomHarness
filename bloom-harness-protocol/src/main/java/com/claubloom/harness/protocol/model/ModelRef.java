package com.claubloom.harness.protocol.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Reference to a specific AI model provider and model ID.
 */
public record ModelRef(
        @JsonProperty(value = "provider", required = true)
        String provider,
        @JsonProperty(value = "id", required = true)
        String id
) {
    public static ModelRef of(String provider, String id) {
        return new ModelRef(provider, id);
    }
}
