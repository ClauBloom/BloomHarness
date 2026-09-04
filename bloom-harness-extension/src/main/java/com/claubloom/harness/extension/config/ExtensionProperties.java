package com.claubloom.harness.extension.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * bloom-harness-extension 模块的配置属性。
 */
@Data
@ConfigurationProperties(prefix = "bloom.extension")
public class ExtensionProperties {

    /**
     * Whether extension system is enabled.
     */
    private boolean enabled = true;

    /**
     * 外部扩展目录或 jar 插件包的路径列表。
     */
    private List<String> pluginPaths = new ArrayList<>();
}
