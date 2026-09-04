package com.claubloom.harness.core.event;

import com.claubloom.harness.protocol.message.AgentMessage;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 智能体执行循环结束完成时触发的事件。
 */
public record AgentEndEvent(
        @JsonProperty(value = "type", defaultValue = "agent_end")
        String type,
        @JsonProperty(value = "messages", required = true)
        List<AgentMessage> messages
) implements AgentEvent {
    public AgentEndEvent(List<AgentMessage> messages) {
        this("agent_end", messages);
    }
}
