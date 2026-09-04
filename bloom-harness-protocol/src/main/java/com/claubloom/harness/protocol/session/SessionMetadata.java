package com.claubloom.harness.protocol.session;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Lightweight metadata of a session.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionMetadata(
        @JsonProperty(value = "id", required = true)
        String id,
        @JsonProperty(value = "createdAt", required = true)
        long createdAt,
        @JsonProperty("updatedAt")
        Long updatedAt,
        @JsonProperty("parentSessionId")
        String parentSessionId,
        @JsonProperty("sessionName")
        String sessionName,
        @JsonProperty("cwd")
        String cwd
) {
}
