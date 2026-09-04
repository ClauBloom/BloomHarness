package com.claubloom.harness.protocol.content;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Image message content item (base64 encoded).
 */
public record ImageContent(
        @JsonProperty(value = "type", defaultValue = "image")
        String type,
        @JsonProperty(value = "data", required = true)
        String data,
        @JsonProperty(value = "mimeType", required = true)
        String mimeType
) implements MessageContent {

    public ImageContent(String data, String mimeType) {
        this("image", data, mimeType);
    }
}
