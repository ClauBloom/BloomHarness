package com.claubloom.harness.protocol.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 详细的错误信息描述。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorInfo(
        @JsonProperty(value = "code", required = true)
        String code,
        @JsonProperty(value = "message", required = true)
        String message,
        @JsonProperty("details")
        Object details
) {
    public static ErrorInfo of(String code, String message) {
        return new ErrorInfo(code, message, null);
    }

    public static ErrorInfo of(String code, String message, Object details) {
        return new ErrorInfo(code, message, details);
    }
}
