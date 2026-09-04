package com.claubloom.harness.protocol.envelope;

import com.claubloom.harness.protocol.session.ServerSnapshot;
import com.claubloom.harness.protocol.session.SessionSnapshot;
import com.claubloom.harness.protocol.stream.TranscriptProgress;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 所有服务端广播事件的密封基接口。
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ServerEvent.ServerSnapshotEvent.class, name = "server_snapshot"),
        @JsonSubTypes.Type(value = ServerEvent.SessionSnapshotEvent.class, name = "session_snapshot"),
        @JsonSubTypes.Type(value = ServerEvent.SessionProgressEvent.class, name = "session_progress"),
        @JsonSubTypes.Type(value = ServerEvent.SessionRemovedEvent.class, name = "session_removed")
})
public sealed interface ServerEvent
        permits ServerEvent.ServerSnapshotEvent, ServerEvent.SessionSnapshotEvent,
                ServerEvent.SessionProgressEvent, ServerEvent.SessionRemovedEvent {

    String type();

    record ServerSnapshotEvent(
            @JsonProperty(value = "type", defaultValue = "server_snapshot")
            String type,
            @JsonProperty(value = "snapshot", required = true)
            ServerSnapshot snapshot
    ) implements ServerEvent {
        public ServerSnapshotEvent(ServerSnapshot snapshot) {
            this("server_snapshot", snapshot);
        }
    }

    record SessionSnapshotEvent(
            @JsonProperty(value = "type", defaultValue = "session_snapshot")
            String type,
            @JsonProperty(value = "snapshot", required = true)
            SessionSnapshot snapshot
    ) implements ServerEvent {
        public SessionSnapshotEvent(SessionSnapshot snapshot) {
            this("session_snapshot", snapshot);
        }
    }

    record SessionProgressEvent(
            @JsonProperty(value = "type", defaultValue = "session_progress")
            String type,
            @JsonProperty(value = "sessionId", required = true)
            String sessionId,
            @JsonProperty(value = "progress", required = true)
            TranscriptProgress progress
    ) implements ServerEvent {
        public SessionProgressEvent(String sessionId, TranscriptProgress progress) {
            this("session_progress", sessionId, progress);
        }
    }

    record SessionRemovedEvent(
            @JsonProperty(value = "type", defaultValue = "session_removed")
            String type,
            @JsonProperty(value = "sessionId", required = true)
            String sessionId
    ) implements ServerEvent {
        public SessionRemovedEvent(String sessionId) {
            this("session_removed", sessionId);
        }
    }
}
