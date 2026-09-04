package com.claubloom.harness.core.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 当 a single Turn within the Agent Loop begins 时触发的事件。
 */
public record TurnStartEvent(
        @JsonProperty(value = "type", defaultValue = "turn_start")
        String type
) implements AgentEvent {
    public TurnStartEvent() {
        this("turn_start");
    }
}
