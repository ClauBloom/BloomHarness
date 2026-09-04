package com.claubloom.harness.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON-RPC 2.0 错误数据对象。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcError(
        @JsonProperty(value = "code", required = true)
        int code,
        @JsonProperty(value = "message", required = true)
        String message,
        @JsonProperty("data")
        Object data
) {}
