package com.claubloom.harness.protocol.command;

import com.claubloom.harness.protocol.model.ModelRef;
import com.claubloom.harness.protocol.session.ThinkingLevel;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Create a new session.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateCommand(
        @JsonProperty(value = "command", defaultValue = "create")
        String command,
        @JsonProperty("cwd")
        String cwd,
        @JsonProperty("name")
        String name,
        @JsonProperty("model")
        ModelRef model,
        @JsonProperty("thinkingLevel")
        ThinkingLevel thinkingLevel
) implements Command {
    public CreateCommand(String cwd, String name, ModelRef model, ThinkingLevel thinkingLevel) {
        this("create", cwd, name, model, thinkingLevel);
    }
}
