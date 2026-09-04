package com.claubloom.harness.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * bloom-harness-mcp 模块的配置属性。
 */
@Data
@ConfigurationProperties(prefix = "bloom.mcp")
public class McpProperties {

    /**
     * Whether MCP integration is enabled.
     */
    private boolean enabled = true;

    /**
     * Map of server name to server configuration.
     */
    private Map<String, ServerProperties> servers = new HashMap<>();

    @Data
    public static class ServerProperties {
        private String transport = "stdio"; // 通信传输模式: stdio (标准输入输出) 或 sse (Server-Sent Events)
        private String command;
        private List<String> args;
        private Map<String, String> env = new HashMap<>();
        private String url;
    }
}
