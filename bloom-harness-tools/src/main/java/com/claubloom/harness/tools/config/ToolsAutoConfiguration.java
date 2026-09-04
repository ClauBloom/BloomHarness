package com.claubloom.harness.tools.config;

import com.claubloom.harness.core.tool.ToolDefinition;
import com.claubloom.harness.core.tool.ToolRegistry;
import com.claubloom.harness.tools.builtin.*;
import com.claubloom.harness.tools.sandbox.PathSandbox;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * bloom-harness-tools 模块的 Spring Boot 自动装配类。
 * 负责初始化内置工具集并自动挂载至 ToolRegistry 注册表。
 */
@AutoConfiguration
@EnableConfigurationProperties(ToolsProperties.class)
public class ToolsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PathSandbox pathSandbox(ToolsProperties properties) {
        Path root = Paths.get(properties.getWorkspaceRoot());
        List<Path> extraRoots = new ArrayList<>();
        if (properties.getAllowedRoots() != null) {
            for (String extra : properties.getAllowedRoots()) {
                extraRoots.add(Paths.get(extra));
            }
        }
        return new PathSandbox(root, extraRoots, properties.isSandboxEnabled());
    }

    @Bean
    @ConditionalOnMissingBean
    public ReadTool readTool(PathSandbox pathSandbox) {
        return new ReadTool(pathSandbox);
    }

    @Bean
    @ConditionalOnMissingBean
    public WriteTool writeTool(PathSandbox pathSandbox) {
        return new WriteTool(pathSandbox);
    }

    @Bean
    @ConditionalOnMissingBean
    public EditTool editTool(PathSandbox pathSandbox) {
        return new EditTool(pathSandbox);
    }

    @Bean
    @ConditionalOnMissingBean
    public GrepTool grepTool(PathSandbox pathSandbox) {
        return new GrepTool(pathSandbox);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobTool globTool(PathSandbox pathSandbox) {
        return new GlobTool(pathSandbox);
    }

    @Bean
    @ConditionalOnMissingBean
    public BashTool bashTool(PathSandbox pathSandbox) {
        return new BashTool(pathSandbox);
    }

    @Bean
    public Object toolRegistryInitializer(
            ToolRegistry toolRegistry,
            List<ToolDefinition> tools
    ) {
        if (toolRegistry != null && tools != null) {
            for (ToolDefinition tool : tools) {
                toolRegistry.register(tool);
            }
        }
        return new Object();
    }
}
