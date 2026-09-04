package com.claubloom.harness.mcp.model;

import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Configuration for an upstream MCP Server.
 */
@Builder
public record McpServerConfig(
        String name,
        String transport, // 通信传输模式: stdio (标准输入输出) 或 sse (Server-Sent Events)
        String command,
        List<String> args,
        Map<String, String> env,
        String url
) {
    public McpServerConfig {
        transport = transport != null ? transport : "stdio";
        args = args != null ? List.copyOf(args) : List.of();
        env = env != null ? Map.copyOf(env) : Map.of();
    }
}
