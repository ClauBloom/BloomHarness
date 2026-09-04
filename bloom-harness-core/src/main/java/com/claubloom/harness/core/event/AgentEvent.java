package com.claubloom.harness.core.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 智能体循环所有核心生命周期事件的密封基接口。
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AgentStartEvent.class, name = "agent_start"),
        @JsonSubTypes.Type(value = AgentEndEvent.class, name = "agent_end"),
        @JsonSubTypes.Type(value = TurnStartEvent.class, name = "turn_start"),
        @JsonSubTypes.Type(value = TurnEndEvent.class, name = "turn_end"),
        @JsonSubTypes.Type(value = MessageStartEvent.class, name = "message_start"),
        @JsonSubTypes.Type(value = MessageUpdateEvent.class, name = "message_update"),
        @JsonSubTypes.Type(value = MessageEndEvent.class, name = "message_end"),
        @JsonSubTypes.Type(value = ToolCallEvent.class, name = "tool_call"),
        @JsonSubTypes.Type(value = ToolResultEvent.class, name = "tool_result")
})
public sealed interface AgentEvent
        permits AgentStartEvent, AgentEndEvent, TurnStartEvent, TurnEndEvent,
                MessageStartEvent, MessageUpdateEvent, MessageEndEvent,
                ToolCallEvent, ToolResultEvent {

    String type();
}
