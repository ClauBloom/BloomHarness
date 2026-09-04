package com.claubloom.harness.protocol.command;

import com.claubloom.harness.protocol.session.SessionSnapshot;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 提示词提交命令的执行结果。
 */
public record PromptResult(
        @JsonProperty(value = "command", defaultValue = "prompt")
        String command,
        @JsonProperty(value = "session", required = true)
        SessionSnapshot session
) implements CommandResult {
    public PromptResult(SessionSnapshot session) {
        this("prompt", session);
    }
}
