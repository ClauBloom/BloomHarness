package com.claubloom.harness.core.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 智能体执行循环开始启动时触发的事件。
 */
public record AgentStartEvent(
        @JsonProperty(value = "type", defaultValue = "agent_start")
        String type
) implements AgentEvent {
    public AgentStartEvent() {
        this("agent_start");
    }
}
