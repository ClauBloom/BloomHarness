package com.claubloom.harness.server.errors;

import static com.claubloom.harness.server.errors.PiServerErrors.INTERNAL_SERVER_ERROR_MESSAGE;

/**
 * 不可安全序列化的内部失败异常。原因会被保留用于日志上报，
 * 但在发送到客户端时仅展示通用错误消息。
 */
public class InternalServerError extends RuntimeException {

    public InternalServerError(Throwable cause) {
        super(INTERNAL_SERVER_ERROR_MESSAGE, cause);
    }
}
