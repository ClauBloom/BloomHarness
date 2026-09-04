package com.claubloom.harness.ai.exception;

/**
 * BloomHarness 系统中 AI 模型调用的具体领域异常定义集合。
 */
public final class AiExceptions {

    private AiExceptions() {}

    /**
     * 网络不可达、DNS 域名解析失败或对端连接被拒绝。
     */
    public static class NetworkException extends BloomAiException {
        public NetworkException(String message, String details, Throwable cause) {
            super("AI_NETWORK_ERROR", 0, message, "请检查网络连接、BaseURL 是否拼写正确，或测试该地址能否直接访问。", true, details, cause);
        }
    }

    /**
     * 涵盖连接握手超时、首包响应等待超时与流式传输空闲超时的超时异常。
     */
    public static class TimeoutException extends BloomAiException {
        public TimeoutException(String phase, String message, String details) {
            super("AI_TIMEOUT_" + phase.toUpperCase(), 408, message, "上游响应超时。可尝试切换网络、重新提问或缩减上下文。", true, details);
        }
    }

    /**
     * 上游鉴权失败或访问权限被拒绝（HTTP 401 / 403）。
     */
    public static class AuthException extends BloomAiException {
        public AuthException(int statusCode, String message, String details) {
            super("AI_AUTH_FAILED", statusCode, message, "请点击右上角设置 (⚙️) 检查并重新填写该服务商的有效 API Key。", false, details);
        }
    }

    /**
     * 服务商账户可用额度或余额已耗尽（HTTP 402）。
     */
    public static class QuotaExhaustedException extends BloomAiException {
        public QuotaExhaustedException(String message, String details) {
            super("AI_INSUFFICIENT_QUOTA", 402, message, "您的 AI 服务商账户余额已耗尽，请前往对应服务商控制台充值。", false, details);
        }
    }

    /**
     * 触发服务商调用频率或并发/Token 上限限制（HTTP 429）。
     */
    public static class RateLimitException extends BloomAiException {
        public RateLimitException(String message, String details) {
            super("AI_RATE_LIMIT", 429, message, "请求过于频繁或触发并发/Token 上限，请等待数秒后重试。", true, details);
        }
    }

    /**
     * 请求参数错误（HTTP 400），例如模型名称不存在或单轮上下文超长。
     */
    public static class BadRequestException extends BloomAiException {
        public BadRequestException(String message, String details) {
            super("AI_BAD_REQUEST", 400, message, "请求参数异常或该模型不存在，请检查模型名称是否输入正确。", false, details);
        }
    }

    /**
     * 服务商服务端内部异常或网关网管故障（HTTP 500/502/503/504）。
     */
    public static class UpstreamServerException extends BloomAiException {
        public UpstreamServerException(int statusCode, String message, String details) {
            super("AI_UPSTREAM_ERROR", statusCode, message, "上游模型服务出现内部故障或正在维护，请稍后重试或切换其他服务商。", true, details);
        }
    }
}
