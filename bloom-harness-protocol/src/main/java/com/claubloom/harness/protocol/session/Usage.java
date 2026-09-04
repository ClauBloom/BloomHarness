package com.claubloom.harness.protocol.session;

import com.claubloom.harness.protocol.model.ModelCost;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Token usage and cost accounting.
 */
public record Usage(
        @JsonProperty(value = "input", defaultValue = "0")
        int input,
        @JsonProperty(value = "output", defaultValue = "0")
        int output,
        @JsonProperty(value = "cacheRead", defaultValue = "0")
        int cacheRead,
        @JsonProperty(value = "cacheWrite", defaultValue = "0")
        int cacheWrite,
        @JsonProperty("reasoning")
        Integer reasoning,
        @JsonProperty(value = "totalTokens", defaultValue = "0")
        int totalTokens,
        @JsonProperty(value = "cost", required = true)
        ModelCost cost
) {
    public static Usage zero() {
        return new Usage(0, 0, 0, 0, null, 0, ModelCost.zero());
    }

    public Usage add(Usage other) {
        if (other == null) {
            return this;
        }
        int newReasoning = (this.reasoning != null ? this.reasoning : 0) + (other.reasoning != null ? other.reasoning : 0);
        return new Usage(
                this.input + other.input,
                this.output + other.output,
                this.cacheRead + other.cacheRead,
                this.cacheWrite + other.cacheWrite,
                newReasoning > 0 ? newReasoning : null,
                this.totalTokens + other.totalTokens,
                new ModelCost(
                        this.cost.input() + other.cost.input(),
                        this.cost.output() + other.cost.output(),
                        this.cost.cacheRead() + other.cost.cacheRead(),
                        this.cost.cacheWrite() + other.cost.cacheWrite(),
                        this.cost.total() + other.cost.total()
                )
        );
    }
}
