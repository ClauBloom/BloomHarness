package com.claubloom.harness.protocol.envelope;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed interface for all messages sent from client to server.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ClientHello.class, name = "hello"),
        @JsonSubTypes.Type(value = RequestEnvelope.class, name = "request")
})
public sealed interface ClientMessage permits ClientHello, RequestEnvelope {
    String type();
}
