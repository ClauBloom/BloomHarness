package com.claubloom.harness.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * BloomHarness application entry point.
 * Aggregates protocol, core, ai-adapter, tools, skills, extension, mcp, storage, and server modules.
 */
@SpringBootApplication(scanBasePackages = "com.claubloom.harness")
public class BloomHarnessApplication {

    public static void main(String[] args) {
        SpringApplication.run(BloomHarnessApplication.class, args);
    }
}
