package com.claubloom.harness.protocol.command;

import com.claubloom.harness.protocol.session.SessionSnapshot;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * set_model 命令的结果。
 */
public record SetModelResult(
        @JsonProperty(value = "command", defaultValue = "set_model")
        String command,
        @JsonProperty(value = "session", required = true)
        SessionSnapshot session
) implements CommandResult {
    public SetModelResult(SessionSnapshot session) {
        this("set_model", session);
    }
}
