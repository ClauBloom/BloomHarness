package com.claubloom.harness.protocol.session;

import com.claubloom.harness.protocol.model.ModelMetadata;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 服务器状态的权威快照,包含所有会话与可用模型。
 */
public record ServerSnapshot(
        @JsonProperty(value = "serverId", required = true)
        String serverId,
        @JsonProperty(value = "protocolVersion", defaultValue = "1")
        int protocolVersion,
        @JsonProperty(value = "revision", defaultValue = "0")
        int revision,
        @JsonProperty(value = "sessions", required = true)
        List<SessionMetadata> sessions,
        @JsonProperty(value = "models", required = true)
        List<ModelMetadata> models
) {
    public static final int PROTOCOL_VERSION = 1;

    public ServerSnapshot(String serverId, int revision, List<SessionMetadata> sessions, List<ModelMetadata> models) {
        this(serverId, PROTOCOL_VERSION, revision, sessions, models);
    }
}
