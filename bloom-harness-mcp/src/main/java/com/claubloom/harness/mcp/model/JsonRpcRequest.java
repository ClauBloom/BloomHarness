package com.claubloom.harness.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.Map;

/**
 * JSON-RPC 2.0 Request message.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcRequest(
        @JsonProperty(value = "jsonrpc", defaultValue = "2.0")
        String jsonrpc,
        @JsonProperty(value = "id", required = true)
        Object id,
        @JsonProperty(value = "method", required = true)
        String method,
        @JsonProperty("params")
        Map<String, Object> params
) {
    public JsonRpcRequest(Object id, String method, Map<String, Object> params) {
        this("2.0", id, method, params);
    }
}
