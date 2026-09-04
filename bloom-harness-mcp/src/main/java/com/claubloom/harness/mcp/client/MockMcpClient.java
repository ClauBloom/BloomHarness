package com.claubloom.harness.mcp.client;

import com.claubloom.harness.mcp.model.McpToolInfo;
import com.claubloom.harness.protocol.content.TextContent;
import com.claubloom.harness.protocol.tool.ToolResult;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * In-memory Mock MCP Client for offline testing and deterministic validation.
 */
public class MockMcpClient implements McpClient {

    @Getter
    private final String serverName;
    private final Map<String, McpToolInfo> registeredTools = new ConcurrentHashMap<>();
    private final Map<String, Function<Map<String, Object>, ToolResult>> handlers = new ConcurrentHashMap<>();
    private boolean initialized = false;

    public MockMcpClient(String serverName) {
        this.serverName = serverName;
    }

    public void addTool(String name, String description, Map<String, Object> inputSchema,
                        Function<Map<String, Object>, ToolResult> handler) {
        registeredTools.put(name, new McpToolInfo(name, description, inputSchema));
        handlers.put(name, handler);
    }

    @Override
    public CompletableFuture<Map<String, Object>> initialize() {
        this.initialized = true;
        return CompletableFuture.completedFuture(Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of("tools", Map.of()),
                "serverInfo", Map.of("name", serverName, "version", "1.0.0")
        ));
    }

    @Override
    public CompletableFuture<List<McpToolInfo>> listTools() {
        if (!initialized) {
            return CompletableFuture.failedFuture(new IllegalStateException("MCP Client not initialized"));
        }
        return CompletableFuture.completedFuture(new ArrayList<>(registeredTools.values()));
    }

    @Override
    public CompletableFuture<ToolResult> callTool(String name, Map<String, Object> arguments) {
        if (!initialized) {
            return CompletableFuture.completedFuture(ToolResult.error("MCP Client not initialized"));
        }
        var handler = handlers.get(name);
        if (handler == null) {
            return CompletableFuture.completedFuture(ToolResult.error("Tool '" + name + "' not found on MCP server " + serverName));
        }
        try {
            ToolResult result = handler.apply(arguments != null ? arguments : Map.of());
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(ToolResult.error("Tool execution failed: " + e.getMessage()));
        }
    }

    @Override
    public void close() {
        this.initialized = false;
    }
}
