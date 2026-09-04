package com.claubloom.harness.core.tool;

import com.claubloom.harness.core.loop.AgentContext;
import com.claubloom.harness.core.loop.AgentEventSink;

/**
 * Context provided to a tool during invocation.
 */
public record ToolContext(
        String sessionId,
        String cwd,
        AgentContext agentContext,
        AgentEventSink eventSink
) {
}
