package com.claubloom.harness.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * BloomHarness Core 引擎的配置属性。
 */
@Data
@ConfigurationProperties(prefix = "bloom-harness.core")
public class CoreProperties {

    /**
     * 单个智能体执行循环允许的最大轮次。
     */
    private int maxTurns = 100;

    /**
     * 触发上下文压缩的上下文窗口用量阈值比例（0.0 - 1.0）。
     */
    private double compactionThreshold = 0.8;

    /**
     * 压缩期间完整保留的最近消息条数。
     */
    private int compactionRetainedTail = 6;
}
