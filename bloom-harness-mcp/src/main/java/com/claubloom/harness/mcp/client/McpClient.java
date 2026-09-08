package com.claubloom.harness.mcp.client;

import com.claubloom.harness.mcp.model.McpToolInfo;
import com.claubloom.harness.protocol.tool.ToolResult;

import java.io.Closeable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 与上游 Model Context Protocol (MCP) Server 通信的客户端接口。
 */
public interface McpClient extends Closeable {

    /**
     * 发送 initialize JSON-RPC 握手以协商能力。
     */
    CompletableFuture<Map<String, Object>> initialize();

    /**
     * 获取 MCP Server 暴露的工具列表。
     */
    CompletableFuture<List<McpToolInfo>> listTools();

    /**
     * 按名称并携带给定参数调用 MCP 工具。
     */
    CompletableFuture<ToolResult> callTool(String name, Map<String, Object> arguments);

    /**
     * 服务器名称 / 标识符。
     */
    String getServerName();
}
