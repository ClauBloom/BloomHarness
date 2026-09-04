package com.claubloom.harness.protocol.command;

import com.claubloom.harness.protocol.session.SessionSnapshot;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 会话创建命令的执行结果。
 */
public record CreateResult(
        @JsonProperty(value = "command", defaultValue = "create")
        String command,
        @JsonProperty(value = "session", required = true)
        SessionSnapshot session
) implements CommandResult {
    public CreateResult(SessionSnapshot session) {
        this("create", session);
    }
}
