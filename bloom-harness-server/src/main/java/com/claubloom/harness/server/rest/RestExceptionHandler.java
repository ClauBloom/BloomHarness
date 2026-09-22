package com.claubloom.harness.server.rest;

import com.claubloom.harness.protocol.codec.ProtocolValidationError;
import com.claubloom.harness.protocol.result.ProtocolError;
import com.claubloom.harness.protocol.result.ProtocolErrorCode;
import com.claubloom.harness.server.errors.InternalServerError;
import com.claubloom.harness.server.errors.PiServerError;
import com.claubloom.harness.server.errors.PiServerErrors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * REST 层的全局异常映射：把可跨协议边界的业务错误翻译为语义等价的 HTTP 状态码。
 * <p>
 * 修复前 REST 端点没有任何异常处理，{@link PiServerError}（not_found / busy /
 * session_locked）这类**正常的业务结果**被 Spring 默认压成 500，客户端无法区分
 * "资源不存在"与"服务端内部错误"；同时错误日志被可预期的业务分支污染。
 * <p>
 * 错误负载复用 {@link PiServerErrors#toProtocolError}，与 WebSocket 协议通道
 * 保持同一套错误语义，客户端无需维护两套判断逻辑。
 */
@Slf4j
@RestControllerAdvice
public class RestExceptionHandler {

    /**
     * 将可安全序列化的业务异常映射为对应的 HTTP 状态码。
     */
    @ExceptionHandler(PiServerError.class)
    public ResponseEntity<ProtocolError> handlePiServerError(PiServerError error) {
        ProtocolErrorCode code = error.getCode();
        HttpStatus status = toHttpStatus(code);
        log.debug("已映射业务异常：code={} -> HTTP {}", code != null ? code.getValue() : null, status.value());
        return ResponseEntity.status(status).body(PiServerErrors.toProtocolError(error));
    }

    /**
     * 将非法入参映射为 400 而非 500。
     * <p>
     * 覆盖两类来源：协议层抛出的 {@code ProtocolValidationError}（解码出的值不是
     * 有效协议消息）与参数校验抛出的 {@link IllegalArgumentException}。
     * 两者都属于调用方可纠正的错误，不应以 500 上报。
     * <p>
     * 注意：此处不能直接复用 {@link PiServerErrors#toProtocolError}——它只识别
     * {@code PiServerError} 与 {@code ProtocolValidationError}，其余一律回退为
     * {@code internal_error}，会与 400 状态码自相矛盾。
     */
    @ExceptionHandler({ProtocolValidationError.class, IllegalArgumentException.class})
    public ResponseEntity<ProtocolError> handleBadRequest(RuntimeException error) {
        log.debug("已映射非法入参：{} -> HTTP 400", error.getMessage());
        return ResponseEntity.badRequest()
                .body(ProtocolError.of(ProtocolErrorCode.INVALID_REQUEST, error.getMessage()));
    }

    /**
     * 兜底：不可安全序列化的内部失败。
     * <p>
     * 原因记入日志供排查，但**绝不**随响应体返回给客户端，避免泄漏内部实现细节。
     */
    @ExceptionHandler(InternalServerError.class)
    public ResponseEntity<ProtocolError> handleInternalServerError(InternalServerError error) {
        log.error("内部错误", error.getCause() != null ? error.getCause() : error);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(PiServerErrors.toProtocolError(error));
    }

    /** 协议错误码到 HTTP 状态码的语义等价映射。 */
    private static HttpStatus toHttpStatus(ProtocolErrorCode code) {
        if (code == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return switch (code) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case BUSY -> HttpStatus.CONFLICT;
            case SESSION_LOCKED -> HttpStatus.LOCKED;
            case INVALID_REQUEST, VERSION -> HttpStatus.BAD_REQUEST;
            case NOT_IMPLEMENTED -> HttpStatus.NOT_IMPLEMENTED;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
