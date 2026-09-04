package com.claubloom.harness.protocol.command;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 会话分离命令的执行结果。
 */
public record DetachResult(
        @JsonProperty(value = "command", defaultValue = "detach")
        String command,
        @JsonProperty(value = "sessionId", required = true)
        String sessionId
) implements CommandResult {
    public DetachResult(String sessionId) {
        this("detach", sessionId);
    }
}
