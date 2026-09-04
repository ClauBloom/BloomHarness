package com.claubloom.harness.protocol.command;

import com.claubloom.harness.protocol.session.SessionSnapshot;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of set_thinking command.
 */
public record SetThinkingResult(
        @JsonProperty(value = "command", defaultValue = "set_thinking")
        String command,
        @JsonProperty(value = "session", required = true)
        SessionSnapshot session
) implements CommandResult {
    public SetThinkingResult(SessionSnapshot session) {
        this("set_thinking", session);
    }
}
