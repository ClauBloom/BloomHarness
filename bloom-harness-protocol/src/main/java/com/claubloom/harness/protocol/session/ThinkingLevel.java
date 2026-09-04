package com.claubloom.harness.protocol.session;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Thinking level options for reasoning models.
 */
public enum ThinkingLevel {
    OFF("off"),
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh"),
    MAX("max");

    private final String value;

    ThinkingLevel(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ThinkingLevel fromValue(String value) {
        if (value == null) {
            return OFF;
        }
        for (ThinkingLevel level : values()) {
            if (level.value.equalsIgnoreCase(value)) {
                return level;
            }
        }
        return OFF;
    }
}
