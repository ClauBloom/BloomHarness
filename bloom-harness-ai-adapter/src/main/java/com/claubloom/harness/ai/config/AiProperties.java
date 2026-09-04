package com.claubloom.harness.ai.config;

import com.claubloom.harness.ai.provider.ProviderConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for AI adapters and providers.
 */
@Data
@ConfigurationProperties(prefix = "bloom-harness.ai")
public class AiProperties {

    /**
     * 默认使用的模型引用标识（可选，格式如 provider:model）。
     */
    private String defaultModel = "";

    /**
     * Default thinking level (off, low, medium, high, max).
     */
    private String defaultThinkingLevel = "off";

    /**
     * List of configured upstream AI providers.
     */
    private List<ProviderConfig> providers = new ArrayList<>();
}
