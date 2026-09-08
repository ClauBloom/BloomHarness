package com.claubloom.harness.server.errors;

import com.claubloom.harness.protocol.result.ProtocolErrorCode;

/** 会话已被其他操作锁定时抛出 (code: "session_locked")。 */
public class SessionLockedError extends PiServerError {

    public SessionLockedError() {
        this("Session is locked");
    }

    public SessionLockedError(String message) {
        super(ProtocolErrorCode.SESSION_LOCKED, message);
    }
}
