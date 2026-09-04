package com.claubloom.harness.core.loop;

import com.claubloom.harness.protocol.message.AgentMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable session execution context during Agent Loop.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext {

    private String sessionId;

    private String cwd;

    @Builder.Default
    private List<AgentMessage> messages = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> attributes = new ConcurrentHashMap<>();

    public AgentContext(String sessionId, String cwd, List<AgentMessage> messages) {
        this.sessionId = sessionId;
        this.cwd = cwd;
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        this.attributes = new ConcurrentHashMap<>();
    }

    public void addMessage(AgentMessage message) {
        this.messages.add(message);
    }
}
