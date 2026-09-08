package com.claubloom.harness.server.errors;

import com.claubloom.harness.protocol.result.ProtocolErrorCode;

/** 会话当前正在执行任务无法接受新操作时抛出 (code: "busy")。 */
public class SessionBusyError extends PiServerError {

    public SessionBusyError() {
        this("Session is busy");
    }

    public SessionBusyError(String message) {
        super(ProtocolErrorCode.BUSY, message);
    }
}
