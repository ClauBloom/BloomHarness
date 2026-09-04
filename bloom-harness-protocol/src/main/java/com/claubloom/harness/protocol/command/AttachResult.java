package com.claubloom.harness.protocol.command;

import com.claubloom.harness.protocol.session.SessionSnapshot;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 会话附加命令的执行结果。
 */
public record AttachResult(
        @JsonProperty(value = "command", defaultValue = "attach")
        String command,
        @JsonProperty(value = "session", required = true)
        SessionSnapshot session
) implements CommandResult {
    public AttachResult(SessionSnapshot session) {
        this("attach", session);
    }
}
