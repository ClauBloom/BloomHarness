package com.claubloom.harness.protocol.command;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 将客户端附加到现有会话。
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
