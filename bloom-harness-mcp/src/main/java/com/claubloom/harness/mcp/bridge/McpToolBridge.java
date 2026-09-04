package com.claubloom.harness.mcp.bridge;

import com.claubloom.harness.core.tool.ToolContext;
import com.claubloom.harness.core.tool.ToolDefinition;
import com.claubloom.harness.mcp.client.McpClient;
import com.claubloom.harness.mcp.model.McpToolInfo;
import com.claubloom.harness.protocol.tool.ToolResult;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Bridges an MCP remote tool into BloomHarness Core's ToolDefinition interface.
 */
@RequiredArgsConstructor
public class McpToolBridge implements ToolDefinition {

    @Getter
    private final String serverName;
    private final McpToolInfo toolInfo;
    private final McpClient client;

    @Override
    public String name() {
        return toolInfo.name();
    }

    @Override
    public String description() {
        return toolInfo.description() != null ? toolInfo.description() : "MCP Tool: " + toolInfo.name();
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return toolInfo.inputSchema();
    }

    @Override
    public CompletableFuture<ToolResult> execute(ToolContext context, Map<String, Object> arguments) {
        return client.callTool(toolInfo.name(), arguments);
    }
}
