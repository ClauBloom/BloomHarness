package com.claubloom.harness.core.config;

import com.claubloom.harness.core.compaction.ContextCompactor;
import com.claubloom.harness.core.loop.AgentLoop;
import com.claubloom.harness.core.prompt.SystemPromptBuilder;
import com.claubloom.harness.core.tool.ToolDefinition;
import com.claubloom.harness.core.tool.ToolExecutor;
import com.claubloom.harness.core.tool.ToolRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Spring Boot AutoConfiguration for BloomHarness Core module.
 */
@AutoConfiguration
@EnableConfigurationProperties(CoreProperties.class)
public class CoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry(List<ToolDefinition> toolDefinitions) {
        return new ToolRegistry(toolDefinitions);
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolExecutor toolExecutor(ToolRegistry toolRegistry) {
        return new ToolExecutor(toolRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentLoop agentLoop(ToolExecutor toolExecutor) {
        return new AgentLoop(toolExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    public ContextCompactor contextCompactor() {
        return new ContextCompactor();
    }

    @Bean
    @ConditionalOnMissingBean
    public SystemPromptBuilder systemPromptBuilder() {
        return new SystemPromptBuilder();
    }
}
