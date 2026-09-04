package com.claubloom.harness.protocol.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Model token cost metrics per million tokens.
 */
public record ModelCost(
        @JsonProperty(value = "input", defaultValue = "0")
        double input,
        @JsonProperty(value = "output", defaultValue = "0")
        double output,
        @JsonProperty(value = "cacheRead", defaultValue = "0")
        double cacheRead,
        @JsonProperty(value = "cacheWrite", defaultValue = "0")
        double cacheWrite,
        @JsonProperty(value = "total", defaultValue = "0")
        double total
) {
    public static ModelCost zero() {
        return new ModelCost(0.0, 0.0, 0.0, 0.0, 0.0);
    }
}
