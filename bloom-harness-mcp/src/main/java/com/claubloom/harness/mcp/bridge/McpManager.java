package com.claubloom.harness.mcp.bridge;

import com.claubloom.harness.core.tool.ToolDefinition;
import com.claubloom.harness.core.tool.ToolRegistry;
import com.claubloom.harness.mcp.client.McpClient;
import com.claubloom.harness.mcp.client.StdioMcpClient;
import com.claubloom.harness.mcp.model.McpServerConfig;
import com.claubloom.harness.mcp.model.McpToolInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Manager manages active MCP server connections, performs discovery,
 * and mounts MCP tools to the Core ToolRegistry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpManager implements Closeable {

    private final ToolRegistry toolRegistry;
    private final Map<String, McpClient> clients = new ConcurrentHashMap<>();
    private final Map<String, List<ToolDefinition>> serverMountedTools = new ConcurrentHashMap<>();

    public synchronized void registerClient(McpClient client) {
        if (client == null) return;
        clients.put(client.getServerName(), client);

        try {
            // 1. Handshake initialize
            Map<String, Object> initResult = client.initialize().get();
            log.info("MCP Server '{}' initialized: {}", client.getServerName(), initResult);

            // 2. Discover tools
            List<McpToolInfo> toolInfos = client.listTools().get();
            List<ToolDefinition> mountedTools = new ArrayList<>();

            for (McpToolInfo toolInfo : toolInfos) {
                McpToolBridge bridge = new McpToolBridge(client.getServerName(), toolInfo, client);
                if (toolRegistry != null) {
                    toolRegistry.register(bridge);
                }
                mountedTools.add(bridge);
                log.info("Mounted MCP Tool '{}:{}' to ToolRegistry", client.getServerName(), toolInfo.name());
            }

            serverMountedTools.put(client.getServerName(), mountedTools);
        } catch (Exception e) {
            log.error("Failed connecting and mounting tools for MCP server '{}': {}", client.getServerName(), e.getMessage(), e);
        }
    }

    public synchronized void registerStdioServer(McpServerConfig config) {
        StdioMcpClient client = new StdioMcpClient(config);
        registerClient(client);
    }

    public Optional<McpClient> getClient(String serverName) {
        return Optional.ofNullable(clients.get(serverName));
    }

    public List<ToolDefinition> getMountedTools(String serverName) {
        return serverMountedTools.getOrDefault(serverName, List.of());
    }

    @Override
    public void close() {
        for (McpClient client : clients.values()) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
        clients.clear();
        serverMountedTools.clear();
    }
}
