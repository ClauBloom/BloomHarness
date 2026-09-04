package com.claubloom.harness.protocol.command;

import com.claubloom.harness.protocol.session.SessionSnapshot;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 方向微调命令的执行结果。
 */
public record SteerResult(
        @JsonProperty(value = "command", defaultValue = "steer")
        String command,
        @JsonProperty(value = "session", required = true)
        SessionSnapshot session
) implements CommandResult {
    public SteerResult(SessionSnapshot session) {
        this("steer", session);
    }
}
