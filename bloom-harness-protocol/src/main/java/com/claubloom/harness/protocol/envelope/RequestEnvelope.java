package com.claubloom.harness.protocol.envelope;

import com.claubloom.harness.protocol.command.Command;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Client request envelope wrapping a command payload.
 */
public record RequestEnvelope(
        @JsonProperty(value = "type", defaultValue = "request")
        String type,
        @JsonProperty(value = "id", required = true)
        String id,
        @JsonProperty(value = "request", required = true)
        Command request
) implements ClientMessage {
    public RequestEnvelope(String id, Command request) {
        this("request", id, request);
    }
}
