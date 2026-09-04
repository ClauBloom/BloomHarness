package com.claubloom.harness.protocol.command;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * List all active/stored sessions.
 */
public record ListCommand(
        @JsonProperty(value = "command", defaultValue = "list")
        String command
) implements Command {
    public ListCommand() {
        this("list");
    }
}
