package com.claubloom.harness.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * JSON-RPC 2.0 Response message.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcResponse(
        @JsonProperty(value = "jsonrpc", defaultValue = "2.0")
        String jsonrpc,
        @JsonProperty("id")
        Object id,
        @JsonProperty("result")
        Object result,
        @JsonProperty("error")
        JsonRpcError error
) {
    public boolean isError() {
        return error != null;
    }

    public static JsonRpcResponse success(Object id, Object result) {
        return new JsonRpcResponse("2.0", id, result, null);
    }

    public static JsonRpcResponse error(Object id, int code, String message) {
        return new JsonRpcResponse("2.0", id, null, new JsonRpcError(code, message, null));
    }
}
