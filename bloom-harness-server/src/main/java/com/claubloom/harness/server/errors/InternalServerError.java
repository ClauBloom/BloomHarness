package com.claubloom.harness.server.errors;

import static com.claubloom.harness.server.errors.PiServerErrors.INTERNAL_SERVER_ERROR_MESSAGE;

/**
 * 一种不安全的失败，其原因被保留用于上报，但绝不会被序列化。
 * 对齐 pi 在 packages/server/src/errors.ts 中的 InternalServerError。
 */
public class InternalServerError extends RuntimeException {

    public InternalServerError(Throwable cause) {
        super(INTERNAL_SERVER_ERROR_MESSAGE, cause);
    }
}
