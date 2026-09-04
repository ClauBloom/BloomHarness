package com.claubloom.harness.mcp.client;

import com.claubloom.harness.mcp.model.McpToolInfo;
import com.claubloom.harness.protocol.tool.ToolResult;

import java.io.Closeable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Client interface for communicating with an upstream Model Context Protocol (MCP) Server.
 */
public interface McpClient extends Closeable {

    /**
     * Send initialize JSON-RPC handshake to negotiate capabilities.
     */
    CompletableFuture<Map<String, Object>> initialize();

    /**
     * Fetch list of tools exposed by the MCP Server.
     */
    CompletableFuture<List<McpToolInfo>> listTools();

    /**
     * Invoke an MCP tool by name with provided arguments.
     */
    CompletableFuture<ToolResult> callTool(String name, Map<String, Object> arguments);

    /**
     * Server name / identifier.
     */
    String getServerName();
}
