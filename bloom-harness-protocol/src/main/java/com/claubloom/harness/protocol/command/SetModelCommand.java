package com.claubloom.harness.protocol.command;

import com.claubloom.harness.protocol.model.ModelRef;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 更改现有会话的活动模型。
 */
public record SetModelCommand(
        @JsonProperty(value = "command", defaultValue = "set_model")
        String command,
        @JsonProperty(value = "sessionId", required = true)
        String sessionId,
        @JsonProperty(value = "model", required = true)
        ModelRef model
) implements Command {
    public SetModelCommand(String sessionId, ModelRef model) {
        this("set_model", sessionId, model);
    }
}
