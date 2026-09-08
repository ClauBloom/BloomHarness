package com.claubloom.harness.server.errors;

import com.claubloom.harness.protocol.result.ProtocolErrorCode;

/** 目标会话不存在时抛出 (code: "not_found")。 */
public class SessionNotFoundError extends PiServerError {

    public SessionNotFoundError() {
        this("Session was not found");
    }

    public SessionNotFoundError(String message) {
        super(ProtocolErrorCode.NOT_FOUND, message);
    }
}
