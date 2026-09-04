package com.claubloom.harness.ai.exception;

import lombok.Getter;

/**
 * BloomHarness 系统中所有 AI 模型调用与路由异常的基础领域异常类。
 */
@Getter
public class BloomAiException extends RuntimeException {

    private final String errorCode;
    private final int statusCode;
    private final String suggestion;
    private final boolean retryable;
    private final String details;

    public BloomAiException(String errorCode, int statusCode, String message, String suggestion, boolean retryable, String details, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
        this.suggestion = suggestion;
        this.retryable = retryable;
        this.details = details;
    }

    public BloomAiException(String errorCode, int statusCode, String message, String suggestion, boolean retryable, String details) {
        this(errorCode, statusCode, message, suggestion, retryable, details, null);
    }
}
