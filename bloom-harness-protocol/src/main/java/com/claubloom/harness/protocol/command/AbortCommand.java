package com.claubloom.harness.protocol.command;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 用于中断会话执行的 Abort 命令。
 */
public record AbortCommand(
        @JsonProperty(value = "command", defaultValue = "abort")
        String command,
        @JsonProperty(value = "sessionId", required = true)
        String sessionId
) implements Command {
    public AbortCommand(String sessionId) {
        this("abort", sessionId);
    }
}
