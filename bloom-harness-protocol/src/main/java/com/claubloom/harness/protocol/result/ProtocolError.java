package com.claubloom.harness.protocol.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard protocol error payload.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProtocolError(
        @JsonProperty(value = "code", required = true)
        ProtocolErrorCode code,
        @JsonProperty(value = "message", required = true)
        String message,
        @JsonProperty("details")
        Object details
) {
    public static ProtocolError of(ProtocolErrorCode code, String message) {
        return new ProtocolError(code, message, null);
    }

    public static ProtocolError of(ProtocolErrorCode code, String message, Object details) {
        return new ProtocolError(code, message, details);
    }
}
