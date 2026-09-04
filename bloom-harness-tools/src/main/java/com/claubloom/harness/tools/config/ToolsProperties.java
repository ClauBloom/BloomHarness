package com.claubloom.harness.tools.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * bloom-harness-tools 模块的配置属性。
 */
@Data
@ConfigurationProperties(prefix = "bloom.tools")
public class ToolsProperties {

    /**
     * 工作区根目录路径。默认采用当前进程运行目录。
     */
    private String workspaceRoot = System.getProperty("user.dir");

    /**
     * PathSandbox 额外允许访问的外部目录白名单路径列表。
     */
    private List<String> allowedRoots = new ArrayList<>();

    /**
     * 是否严格强制开启 PathSandbox 工作区安全隔离沙箱。
     */
    private boolean sandboxEnabled = true;

    /**
     * Bash 命令执行的默认超时时间（毫秒），默认为 60000ms。
     */
    private long defaultBashTimeoutMs = 60_000L;
}
