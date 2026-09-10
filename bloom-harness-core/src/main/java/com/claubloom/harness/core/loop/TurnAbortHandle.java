package com.claubloom.harness.core.loop;

import com.claubloom.harness.protocol.message.AssistantMessage;

import java.util.concurrent.CompletableFuture;

/**
 * 单轮任务的中止令牌：由会话运行时实现并经 {@link AgentLoopConfig} 传入智能体循环。
 * <p>
 * {@link AgentLoop} 在每个轮次边界（循环顶部、工具执行前、每次 LLM 调用前后）轮询
 * {@link #isAborted()}，并调用 {@link #track(CompletableFuture)} 登记当前在途的
 * LLM 调用——运行时在 {@code abort()} 时即可立即 {@code cancel} 在途请求，
 * 中断流式输出、停止消耗上游 Token，而不必等当前响应自然结束。
 */
public interface TurnAbortHandle {

    /** @return 用户是否已请求中止当前轮次 */
    boolean isAborted();

    /**
     * 登记当前进行中的 LLM 调用；传 {@code null} 表示当前没有在途调用。
     * 实现方在中止时应对在途调用执行 {@link CompletableFuture#cancel(boolean)}，
     * 以触发模型适配层对上游流式订阅的释放。
     */
    void track(CompletableFuture<AssistantMessage> activeCall);
}
