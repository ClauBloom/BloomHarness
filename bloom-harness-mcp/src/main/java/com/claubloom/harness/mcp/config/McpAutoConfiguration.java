package com.claubloom.harness.mcp.config;

import com.claubloom.harness.core.tool.ToolRegistry;
import com.claubloom.harness.mcp.bridge.McpManager;
import com.claubloom.harness.mcp.client.McpClient;
import com.claubloom.harness.mcp.model.McpServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

/**
 * bloom-harness-mcp 模块的 Spring Boot 自动装配类。
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(McpProperties.class)
public class McpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public McpManager mcpManager(
            ToolRegistry toolRegistry,
            McpProperties properties,
            List<McpClient> springManagedClients
    ) {
        McpManager manager = new McpManager(toolRegistry);

        if (properties.isEnabled() && properties.getServers() != null) {
            for (Map.Entry<String, McpProperties.ServerProperties> entry : properties.getServers().entrySet()) {
                String serverName = entry.getKey();
                McpProperties.ServerProperties sp = entry.getValue();

                if ("stdio".equalsIgnoreCase(sp.getTransport()) && sp.getCommand() != null) {
                    McpServerConfig config = McpServerConfig.builder()
                            .name(serverName)
                            .transport("stdio")
                            .command(sp.getCommand())
                            .args(sp.getArgs())
                            .env(sp.getEnv())
                            .build();
                    manager.registerStdioServer(config);
                }
            }
        }

        if (springManagedClients != null) {
            for (McpClient client : springManagedClients) {
                manager.registerClient(client);
            }
        }

        log.info("Initialized BloomHarness MCP Subsystem");
        return manager;
    }
}
