package com.claubloom.harness.extension.config;

import com.claubloom.harness.core.tool.ToolRegistry;
import com.claubloom.harness.extension.hook.ExtensionHook;
import com.claubloom.harness.extension.manager.ExtensionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * bloom-harness-extension 模块的 Spring Boot 自动装配类。
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(ExtensionProperties.class)
public class ExtensionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ExtensionManager extensionManager(
            ToolRegistry toolRegistry,
            List<ExtensionHook> springManagedHooks
    ) {
        ExtensionManager manager = new ExtensionManager(toolRegistry);
        if (springManagedHooks != null) {
            for (ExtensionHook hook : springManagedHooks) {
                manager.registerHook(hook);
            }
        }
        log.info("Initialized BloomHarness Extension Manager with {} spring-managed hooks",
                springManagedHooks != null ? springManagedHooks.size() : 0);
        return manager;
    }
}
