package com.claubloom.harness.server.errors;

import com.claubloom.harness.protocol.result.ProtocolError;
import com.claubloom.harness.protocol.result.ProtocolErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 服务器错误层级，对齐 pi 的 packages/server/src/errors.ts。
 * 可安全跨越协议边界的错误继承 {@link PiServerError}；
 * 不安全的失败使用 {@link InternalServerError}，其原因是会上报但绝不序列化的。
 */
public final class PiServerErrors {

    public static final String INTERNAL_SERVER_ERROR_MESSAGE = "Internal server error";
    public static final String NOT_IMPLEMENTED_MESSAGE = "Operation is not implemented";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PiServerErrors() {
    }

    /** 从网络协议代码字符串构造 ProtocolError 异常，对齐 pi-agent 规范。 */
    public static ProtocolError createProtocolError(String code, String message) {
        return ProtocolError.of(ProtocolErrorCode.fromValue(code), message);
    }

    /** 将任意 Throwable 转换为可在网络上传输的 ProtocolError。 */
    public static ProtocolError toProtocolError(Throwable error) {
        if (error instanceof InternalServerError internalServerError) {
            return ProtocolError.of(ProtocolErrorCode.INTERNAL_ERROR, INTERNAL_SERVER_ERROR_MESSAGE);
        }
        if (error instanceof PiServerError piError) {
            if (piError.getCode() == ProtocolErrorCode.NOT_IMPLEMENTED) {
                return ProtocolError.of(ProtocolErrorCode.NOT_IMPLEMENTED, NOT_IMPLEMENTED_MESSAGE);
            }
            return piError.getDetails() == null
                    ? ProtocolError.of(piError.getCode(), piError.getMessage())
                    : new ProtocolError(piError.getCode(), piError.getMessage(),
                            MAPPER.valueToTree(piError.getDetails()));
        }
        if (error instanceof com.claubloom.harness.protocol.codec.ProtocolValidationError validationError) {
            return ProtocolError.of(ProtocolErrorCode.INVALID_REQUEST, validationError.getMessage());
        }
        return ProtocolError.of(ProtocolErrorCode.INTERNAL_ERROR, INTERNAL_SERVER_ERROR_MESSAGE);
    }
}
