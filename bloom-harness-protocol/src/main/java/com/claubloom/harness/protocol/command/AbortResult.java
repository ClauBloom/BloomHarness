package com.claubloom.harness.protocol.command;

import com.claubloom.harness.protocol.session.SessionSnapshot;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 中断任务命令的执行结果。
 */
public record AbortResult(
        @JsonProperty(value = "command", defaultValue = "abort")
        String command,
        @JsonProperty(value = "session", required = true)
        SessionSnapshot session
) implements CommandResult {
    public AbortResult(SessionSnapshot session) {
        this("abort", session);
    }
}
