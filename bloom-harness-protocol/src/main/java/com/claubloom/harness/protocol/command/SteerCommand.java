package com.claubloom.harness.protocol.command;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 在执行轮次期间注入用户引导的 Steer 命令。
 */
public record SteerCommand(
        @JsonProperty(value = "command", defaultValue = "steer")
        String command,
        @JsonProperty(value = "sessionId", required = true)
        String sessionId,
        @JsonProperty(value = "text", required = true)
        String text
) implements Command {
    public SteerCommand(String sessionId, String text) {
        this("steer", sessionId, text);
    }
}
