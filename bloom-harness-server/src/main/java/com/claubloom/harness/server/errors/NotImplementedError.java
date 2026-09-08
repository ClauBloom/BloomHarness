package com.claubloom.harness.server.errors;

import com.claubloom.harness.protocol.result.ProtocolErrorCode;
import static com.claubloom.harness.server.errors.PiServerErrors.NOT_IMPLEMENTED_MESSAGE;

/** 操作尚未实现时抛出的协议错误 (code: "not_implemented")。 */
public class NotImplementedError extends PiServerError {

    public NotImplementedError() {
        super(ProtocolErrorCode.NOT_IMPLEMENTED, NOT_IMPLEMENTED_MESSAGE);
    }
}
