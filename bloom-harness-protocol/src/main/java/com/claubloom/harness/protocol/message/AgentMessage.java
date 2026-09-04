package com.claubloom.harness.protocol.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed interface representing all agent message types.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "role",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = UserMessage.class, name = "user"),
        @JsonSubTypes.Type(value = AssistantMessage.class, name = "assistant"),
        @JsonSubTypes.Type(value = ToolResultMessage.class, name = "tool")
})
public sealed interface AgentMessage
        permits UserMessage, AssistantMessage, ToolResultMessage {

    String id();

    String role();

    long timestamp();
}
