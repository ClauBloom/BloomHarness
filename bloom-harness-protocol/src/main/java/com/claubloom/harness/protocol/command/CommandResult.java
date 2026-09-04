package com.claubloom.harness.protocol.command;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 所有命令执行结果的密封基接口。
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "command"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ListResult.class, name = "list"),
        @JsonSubTypes.Type(value = CreateResult.class, name = "create"),
        @JsonSubTypes.Type(value = AttachResult.class, name = "attach"),
        @JsonSubTypes.Type(value = DetachResult.class, name = "detach"),
        @JsonSubTypes.Type(value = PromptResult.class, name = "prompt"),
        @JsonSubTypes.Type(value = SteerResult.class, name = "steer"),
        @JsonSubTypes.Type(value = AbortResult.class, name = "abort"),
        @JsonSubTypes.Type(value = SetModelResult.class, name = "set_model"),
        @JsonSubTypes.Type(value = SetThinkingResult.class, name = "set_thinking")
})
public sealed interface CommandResult
        permits ListResult, CreateResult, AttachResult, DetachResult,
                PromptResult, SteerResult, AbortResult, SetModelResult, SetThinkingResult {

    String command();
}
