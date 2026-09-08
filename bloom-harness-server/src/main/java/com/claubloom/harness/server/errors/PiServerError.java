package com.claubloom.harness.server.errors;

import com.claubloom.harness.protocol.result.ProtocolError;
import com.claubloom.harness.protocol.result.ProtocolErrorCode;

/**
 * 可安全跨越协议边界传输到客户端的服务/运行时错误基类。
 * 携带结构化的 {@link ProtocolErrorCode} 和可选的详情对象。
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
