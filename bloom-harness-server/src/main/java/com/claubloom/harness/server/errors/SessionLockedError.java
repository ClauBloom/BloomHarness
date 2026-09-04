package com.claubloom.harness.server.errors;

import com.claubloom.harness.protocol.result.ProtocolErrorCode;

/** 对齐 pi-agent 的 SessionLockedError 会话已锁定异常 (code: "session_locked")。 */
public class SessionLockedError extends PiServerError {

    public SessionLockedError() {
        this("Session is locked");
    }

    public SessionLockedError(String message) {
        super(ProtocolErrorCode.SESSION_LOCKED, message);
    }
}
