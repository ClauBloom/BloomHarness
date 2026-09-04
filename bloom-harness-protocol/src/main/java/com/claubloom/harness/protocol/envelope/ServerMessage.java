package com.claubloom.harness.protocol.envelope;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 服务端发往客户端的全部消息报文的密封基接口。
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ServerHello.class, name = "hello"),
        @JsonSubTypes.Type(value = ServerHelloError.class, name = "hello_error"),
        @JsonSubTypes.Type(value = ResponseEnvelope.class, name = "response"),
        @JsonSubTypes.Type(value = EventEnvelope.class, name = "event")
})
public sealed interface ServerMessage
        permits ServerHello, ServerHelloError, ResponseEnvelope, EventEnvelope {
    String type();
}
