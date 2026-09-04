package com.claubloom.harness.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for BloomHarness Core engine.
 */
@Data
@ConfigurationProperties(prefix = "bloom-harness.core")
public class CoreProperties {

    /**
     * Maximum turns allowed per Agent execution loop.
     */
    private int maxTurns = 100;

    /**
     * Threshold ratio (0.0 - 1.0) of context window usage to trigger compaction.
     */
    private double compactionThreshold = 0.8;

    /**
     * Number of recent messages retained in full during compaction.
     */
    private int compactionRetainedTail = 6;
}
