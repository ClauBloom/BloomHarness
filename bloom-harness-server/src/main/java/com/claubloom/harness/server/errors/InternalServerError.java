package com.claubloom.harness.server.errors;

import static com.claubloom.harness.server.errors.PiServerErrors.INTERNAL_SERVER_ERROR_MESSAGE;

/**
 * An unsafe failure whose cause is retained for reporting but never serialized.
 * Mirrors pi's InternalServerError in packages/server/src/errors.ts.
 */
public class InternalServerError extends RuntimeException {

    public InternalServerError(Throwable cause) {
        super(INTERNAL_SERVER_ERROR_MESSAGE, cause);
    }
}
