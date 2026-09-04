package com.claubloom.harness.server.errors;

import com.claubloom.harness.protocol.result.ProtocolErrorCode;

/** 对齐 pi-agent 的 SessionNotFoundError 会话未找到异常 (code: "not_found")。 */
public class SessionNotFoundError extends PiServerError {

    public SessionNotFoundError() {
        this("Session was not found");
    }

    public SessionNotFoundError(String message) {
        super(ProtocolErrorCode.NOT_FOUND, message);
    }
}
