package com.claubloom.harness.protocol.command;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 列出所有活动/已存储的会话。
 */
public record ListCommand(
        @JsonProperty(value = "command", defaultValue = "list")
        String command
) implements Command {
    public ListCommand() {
        this("list");
    }
}
