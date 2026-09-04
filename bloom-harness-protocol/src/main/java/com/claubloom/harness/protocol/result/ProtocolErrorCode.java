package com.claubloom.harness.protocol.result;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Standard protocol error codes.
 */
public enum ProtocolErrorCode {
    VERSION("version"),
    BUSY("busy"),
    SESSION_LOCKED("session_locked"),
    NOT_FOUND("not_found"),
    INVALID_REQUEST("invalid_request"),
    NOT_IMPLEMENTED("not_implemented"),
    INTERNAL_ERROR("internal_error");

    private final String value;

    ProtocolErrorCode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ProtocolErrorCode fromValue(String value) {
        if (value == null) {
            return INTERNAL_ERROR;
        }
        for (ProtocolErrorCode code : values()) {
            if (code.value.equalsIgnoreCase(value)) {
                return code;
            }
        }
        return INTERNAL_ERROR;
    }
}
