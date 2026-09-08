package com.claubloom.harness.protocol.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 对特定 AI 模型供应商与模型 ID 的引用。
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
