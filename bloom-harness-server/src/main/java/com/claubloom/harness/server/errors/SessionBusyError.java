package com.claubloom.harness.server.errors;

import com.claubloom.harness.protocol.result.ProtocolErrorCode;

/** 对齐 pi-agent 的 SessionBusyError 会话正忙异常 (code: "busy")。 */
public class SessionBusyError extends PiServerError {

    public SessionBusyError() {
        this("Session is busy");
    }

    public SessionBusyError(String message) {
        super(ProtocolErrorCode.BUSY, message);
    }
}
