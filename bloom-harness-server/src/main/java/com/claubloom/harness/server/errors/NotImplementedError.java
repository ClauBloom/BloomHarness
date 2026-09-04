package com.claubloom.harness.server.errors;

import com.claubloom.harness.protocol.result.ProtocolErrorCode;
import static com.claubloom.harness.server.errors.PiServerErrors.NOT_IMPLEMENTED_MESSAGE;

/** 对齐 pi-agent 的 NotImplementedError 尚未实现异常 (code: "not_implemented")。 */
public class NotImplementedError extends PiServerError {

    public NotImplementedError() {
        super(ProtocolErrorCode.NOT_IMPLEMENTED, NOT_IMPLEMENTED_MESSAGE);
    }
}
