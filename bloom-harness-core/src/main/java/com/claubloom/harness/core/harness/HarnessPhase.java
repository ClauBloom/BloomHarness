package com.claubloom.harness.core.harness;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Harness runtime phase.
 */
public enum HarnessPhase {
    IDLE("idle"),
    TURN("turn"),
    COMPACTION("compaction"),
    BRANCH_SUMMARY("branch_summary"),
    RETRY("retry");

    private final String value;

    HarnessPhase(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static HarnessPhase fromValue(String value) {
        if (value == null) {
            return IDLE;
        }
        for (HarnessPhase phase : values()) {
            if (phase.value.equalsIgnoreCase(value)) {
                return phase;
            }
        }
        return IDLE;
    }
}
