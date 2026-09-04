package com.claubloom.harness.protocol.command;

import com.claubloom.harness.protocol.session.SessionSnapshot;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of set_model command.
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
