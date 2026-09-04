package com.claubloom.harness.protocol.command;

import com.claubloom.harness.protocol.session.ThinkingLevel;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Change the active thinking level for an existing session.
 */
public record SetThinkingCommand(
        @JsonProperty(value = "command", defaultValue = "set_thinking")
        String command,
        @JsonProperty(value = "sessionId", required = true)
        String sessionId,
        @JsonProperty(value = "thinkingLevel", required = true)
        ThinkingLevel thinkingLevel
) implements Command {
    public SetThinkingCommand(String sessionId, ThinkingLevel thinkingLevel) {
        this("set_thinking", sessionId, thinkingLevel);
    }
}
