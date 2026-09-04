package com.claubloom.harness.protocol.command;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Prompt command to send user instruction to an agent session.
 */
public record PromptCommand(
        @JsonProperty(value = "command", defaultValue = "prompt")
        String command,
        @JsonProperty(value = "sessionId", required = true)
        String sessionId,
        @JsonProperty(value = "text", required = true)
        String text
) implements Command {
    public PromptCommand(String sessionId, String text) {
        this("prompt", sessionId, text);
    }
}
