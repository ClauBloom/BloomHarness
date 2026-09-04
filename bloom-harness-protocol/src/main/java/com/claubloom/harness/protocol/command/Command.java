package com.claubloom.harness.protocol.command;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 所有客户端命令的密封基接口。
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "command"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ListCommand.class, name = "list"),
        @JsonSubTypes.Type(value = CreateCommand.class, name = "create"),
        @JsonSubTypes.Type(value = AttachCommand.class, name = "attach"),
        @JsonSubTypes.Type(value = DetachCommand.class, name = "detach"),
        @JsonSubTypes.Type(value = PromptCommand.class, name = "prompt"),
        @JsonSubTypes.Type(value = SteerCommand.class, name = "steer"),
        @JsonSubTypes.Type(value = AbortCommand.class, name = "abort"),
        @JsonSubTypes.Type(value = SetModelCommand.class, name = "set_model"),
        @JsonSubTypes.Type(value = SetThinkingCommand.class, name = "set_thinking")
})
public sealed interface Command
        permits ListCommand, CreateCommand, AttachCommand, DetachCommand,
                PromptCommand, SteerCommand, AbortCommand, SetModelCommand, SetThinkingCommand {

    String command();
}
