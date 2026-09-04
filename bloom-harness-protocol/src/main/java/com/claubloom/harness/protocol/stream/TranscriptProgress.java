package com.claubloom.harness.protocol.stream;

import com.claubloom.harness.protocol.message.AgentMessage;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Normalized incremental activity for transcript progress events.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TranscriptProgress.ItemStarted.class, name = "item_started"),
        @JsonSubTypes.Type(value = TranscriptProgress.AssistantDelta.class, name = "assistant_delta"),
        @JsonSubTypes.Type(value = TranscriptProgress.ItemUpdated.class, name = "item_updated"),
        @JsonSubTypes.Type(value = TranscriptProgress.ItemFinished.class, name = "item_finished")
})
public sealed interface TranscriptProgress
        permits TranscriptProgress.ItemStarted, TranscriptProgress.AssistantDelta,
                TranscriptProgress.ItemUpdated, TranscriptProgress.ItemFinished {

    String type();

    record ItemStarted(
            @JsonProperty(value = "type", defaultValue = "item_started")
            String type,
            @JsonProperty(value = "item", required = true)
            AgentMessage item
    ) implements TranscriptProgress {
        public ItemStarted(AgentMessage item) {
            this("item_started", item);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AssistantDelta(
            @JsonProperty(value = "type", defaultValue = "assistant_delta")
            String type,
            @JsonProperty(value = "messageId", required = true)
            String messageId,
            @JsonProperty(value = "contentIndex", required = true)
            int contentIndex,
            @JsonProperty(value = "kind", required = true)
            String kind, // text, thinking, toolCall
            @JsonProperty(value = "delta", required = true)
            String delta
    ) implements TranscriptProgress {
        public AssistantDelta(String messageId, int contentIndex, String kind, String delta) {
            this("assistant_delta", messageId, contentIndex, kind, delta);
        }
    }

    record ItemUpdated(
            @JsonProperty(value = "type", defaultValue = "item_updated")
            String type,
            @JsonProperty(value = "item", required = true)
            AgentMessage item
    ) implements TranscriptProgress {
        public ItemUpdated(AgentMessage item) {
            this("item_updated", item);
        }
    }

    record ItemFinished(
            @JsonProperty(value = "type", defaultValue = "item_finished")
            String type,
            @JsonProperty(value = "item", required = true)
            AgentMessage item
    ) implements TranscriptProgress {
        public ItemFinished(AgentMessage item) {
            this("item_finished", item);
        }
    }
}
