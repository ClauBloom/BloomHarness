package com.claubloom.harness.extension.hook;

import com.claubloom.harness.core.loop.AgentContext;
import com.claubloom.harness.protocol.message.AssistantMessage;
import com.claubloom.harness.protocol.tool.ToolResult;

import java.util.Map;

/**
 * Lifecycle hook interface for BloomHarness extensions.
 * Directly mirrors pi's extension lifecycle event subscriptions.
 */
public interface ExtensionHook {

    /**
     * 在一个新的智能体会话开始时调用。
     */
    default void onSessionStart(String sessionId) {}

    /**
     * 在一个智能体会话正常结束或被强行中断时调用。
     */
    default void onSessionEnd(String sessionId) {}

    /**
     * 在每个智能体回合开始前调用（即向大模型发起调用之前）。
     */
    default void beforeTurn(AgentContext context) {}

    /**
     * 在每个智能体回合结束时调用（即接收到完整的大模型响应之后）。
     */
    default void afterTurn(AgentContext context, AssistantMessage assistantMessage) {}

    /**
     * 在工具即将开始执行前立即调用。
     */
    default void beforeToolCall(String toolName, Map<String, Object> arguments) {}

    /**
     * 在工具执行结束并产出结果后立即调用。
     */
    default void afterToolCall(String toolName, Map<String, Object> arguments, ToolResult toolResult) {}
}
