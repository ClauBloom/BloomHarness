package com.claubloom.harness.protocol.envelope;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server event envelope broadcasting server events to connected clients.
 */
public record EventEnvelope(
        @JsonProperty(value = "type", defaultValue = "event")
        String type,
        @JsonProperty(value = "event", required = true)
        ServerEvent event
) implements ServerMessage {
    public EventEnvelope(ServerEvent event) {
        this("event", event);
    }
}
