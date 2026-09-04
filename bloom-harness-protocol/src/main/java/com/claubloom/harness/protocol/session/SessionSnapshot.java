package com.claubloom.harness.protocol.session;

import com.claubloom.harness.protocol.message.AgentMessage;
import com.claubloom.harness.protocol.message.UserMessage;
import com.claubloom.harness.protocol.model.ModelRef;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Authoritative snapshot of a session's full state and transcript.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionSnapshot(
        @JsonProperty(value = "id", required = true)
        String id,
        @JsonProperty("name")
        String name,
        @JsonProperty(value = "cwd", required = true)
        String cwd,
        @JsonProperty(value = "createdAt", required = true)
        long createdAt,
        @JsonProperty(value = "updatedAt", required = true)
        long updatedAt,
        @JsonProperty(value = "phase", required = true)
        SessionPhase phase,
        @JsonProperty(value = "model", required = true)
        ModelRef model,
        @JsonProperty(value = "thinkingLevel", required = true)
        ThinkingLevel thinkingLevel,
        @JsonProperty(value = "attached", defaultValue = "false")
        boolean attached,
        @JsonProperty(value = "locked", defaultValue = "false")
        boolean locked,
        @JsonProperty(value = "revision", defaultValue = "0")
        int revision,
        @JsonProperty(value = "transcript", required = true)
        List<AgentMessage> transcript,
        @JsonProperty(value = "queuedSteer", required = true)
        List<UserMessage> queuedSteer,
        @JsonProperty(value = "queuedSteerCount", defaultValue = "0")
        int queuedSteerCount
) {
}
