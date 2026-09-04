package com.claubloom.harness.protocol.session;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Session execution phase enumeration.
 */
public enum SessionPhase {
    IDLE("idle"),
    TURN("turn"),
    COMPACTION("compaction"),
    BRANCH_SUMMARY("branch_summary"),
    RETRY("retry");

    private final String value;

    SessionPhase(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static SessionPhase fromValue(String value) {
        if (value == null) {
            return IDLE;
        }
        for (SessionPhase phase : values()) {
            if (phase.value.equalsIgnoreCase(value)) {
                return phase;
            }
        }
        return IDLE;
    }
}
