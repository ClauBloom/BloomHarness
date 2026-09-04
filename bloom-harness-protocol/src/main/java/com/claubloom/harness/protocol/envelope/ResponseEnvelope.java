package com.claubloom.harness.protocol.envelope;

import com.claubloom.harness.protocol.command.CommandResult;
import com.claubloom.harness.protocol.result.ProtocolError;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server response envelope answering a request envelope.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponseEnvelope(
        @JsonProperty(value = "type", defaultValue = "response")
        String type,
        @JsonProperty(value = "id", required = true)
        String id,
        @JsonProperty(value = "ok", required = true)
        boolean ok,
        @JsonProperty("result")
        CommandResult result,
        @JsonProperty("error")
        ProtocolError error
) implements ServerMessage {

    public static ResponseEnvelope success(String id, CommandResult result) {
        return new ResponseEnvelope("response", id, true, result, null);
    }

    public static ResponseEnvelope failure(String id, ProtocolError error) {
        return new ResponseEnvelope("response", id, false, null, error);
    }
}
