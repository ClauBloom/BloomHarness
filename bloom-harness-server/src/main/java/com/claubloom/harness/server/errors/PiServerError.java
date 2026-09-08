package com.claubloom.harness.server.errors;

import com.claubloom.harness.protocol.result.ProtocolError;
import com.claubloom.harness.protocol.result.ProtocolErrorCode;

/**
 * 可安全跨越协议边界的服务/运行时错误。
 * 对齐 pi 在 packages/server/src/errors.ts 中的 PiServerError。
 */
public class PiServerError extends RuntimeException {

    private final ProtocolErrorCode code;
    private final transient Object details;

    public PiServerError(ProtocolErrorCode code, String message) {
        this(code, message, null);
    }

    public PiServerError(ProtocolErrorCode code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public ProtocolErrorCode getCode() {
        return code;
    }

    public Object getDetails() {
        return details;
    }
}
