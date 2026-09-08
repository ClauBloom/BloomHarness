package com.claubloom.harness.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * BloomHarness 应用入口。
 * 聚合了 protocol、core、ai-adapter、tools、skills、extension、mcp、storage 与 server 模块。
 */
@SpringBootApplication(scanBasePackages = "com.claubloom.harness")
public class BloomHarnessApplication {

    public static void main(String[] args) {
        SpringApplication.run(BloomHarnessApplication.class, args);
    }
}
