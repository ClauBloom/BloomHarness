package com.claubloom.harness.protocol.command;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Attach client to an existing session.
 */
public record AttachCommand(
        @JsonProperty(value = "command", defaultValue = "attach")
        String command,
        @JsonProperty(value = "sessionId", required = true)
        String sessionId
) implements Command {
    public AttachCommand(String sessionId) {
        this("attach", sessionId);
    }
}
