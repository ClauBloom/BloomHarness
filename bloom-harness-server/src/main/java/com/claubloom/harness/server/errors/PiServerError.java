package com.claubloom.harness.server.errors;

import com.claubloom.harness.protocol.result.ProtocolError;
import com.claubloom.harness.protocol.result.ProtocolErrorCode;

/**
 * A service/runtime error that can safely cross the protocol boundary.
 * Mirrors pi's PiServerError in packages/server/src/errors.ts.
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
