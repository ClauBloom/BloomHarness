package com.claubloom.harness.ai.config;

import com.claubloom.harness.ai.provider.ProviderConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 适配器与上游供应商的配置属性。
 */
@Data
@ConfigurationProperties(prefix = "bloom-harness.ai")
public class AiProperties {

    /**
     * 默认使用的模型引用标识（可选，格式如 provider:model）。
     */
    private String defaultModel = "";

    /**
     * 默认思考级别（off、low、medium、high、max）。
     */
    private String defaultThinkingLevel = "off";

    /**
     * 已配置的上游 AI 供应商列表。
     */
    private List<ProviderConfig> providers = new ArrayList<>();
}
