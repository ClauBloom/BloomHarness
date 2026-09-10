package com.claubloom.harness.core.tool;

import com.claubloom.harness.core.loop.AgentContext;
import com.claubloom.harness.core.loop.AgentEventSink;
import com.claubloom.harness.core.loop.TurnAbortHandle;

/**
 * 工具调用期间提供给工具的上下文。
 *
 * @param abortHandle 中止令牌（可为 null）：长时间运行的工具（如 bash）应周期性轮询
 *                    {@link TurnAbortHandle#isAborted()}，请求中止时尽快终止自身并返回，
 *                    保证前端"终止"按钮对长任务即时生效
 */
public record ToolContext(
        String sessionId,
        String cwd,
        AgentContext agentContext,
        AgentEventSink eventSink,
        TurnAbortHandle abortHandle
) {
    /** 兼容构造：不带中止令牌（工具内部视为不可中止） */
    public ToolContext(String sessionId, String cwd, AgentContext agentContext, AgentEventSink eventSink) {
        this(sessionId, cwd, agentContext, eventSink, null);
    }
}
