package com.claubloom.harness.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.Map;

/**
 * Metadata of a Tool exposed by an MCP Server.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpToolInfo(
        @JsonProperty(value = "name", required = true)
        String name,
        @JsonProperty("description")
        String description,
        @JsonProperty(value = "inputSchema", required = true)
        Map<String, Object> inputSchema
) {
    public McpToolInfo {
        inputSchema = inputSchema != null ? Map.copyOf(inputSchema) : Map.of("type", "object");
    }
}
