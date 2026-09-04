package com.claubloom.harness.protocol.command;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Abort command to interrupt session execution.
 */
public record AbortCommand(
        @JsonProperty(value = "command", defaultValue = "abort")
        String command,
        @JsonProperty(value = "sessionId", required = true)
        String sessionId
) implements Command {
    public AbortCommand(String sessionId) {
        this("abort", sessionId);
    }
}
