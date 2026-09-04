package com.claubloom.harness.protocol.command;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Detach client from a session.
 */
public record DetachCommand(
        @JsonProperty(value = "command", defaultValue = "detach")
        String command,
        @JsonProperty(value = "sessionId", required = true)
        String sessionId
) implements Command {
    public DetachCommand(String sessionId) {
        this("detach", sessionId);
    }
}
