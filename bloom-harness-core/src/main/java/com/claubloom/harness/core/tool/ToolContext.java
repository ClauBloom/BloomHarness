package com.claubloom.harness.core.tool;

import com.claubloom.harness.core.loop.AgentContext;
import com.claubloom.harness.core.loop.AgentEventSink;

/**
 * 工具调用期间提供给工具的上下文。
 */
public record ToolContext(
        String sessionId,
        String cwd,
        AgentContext agentContext,
        AgentEventSink eventSink
) {
}
