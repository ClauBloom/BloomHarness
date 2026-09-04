package com.claubloom.harness.core.loop;

import com.claubloom.harness.protocol.message.AssistantMessage;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for calling LLM (either directly or via stream proxy adapter).
 */
@FunctionalInterface
public interface LlmCaller {

    /**
     * 针对当前回合上下文发起大模型推理调用，并返回组装完成的 AssistantMessage 助手消息。
     *
     * @param context 当前智能体上下文环境（包含历史消息、工作目录与会话标识）
     * @param config 循环配置参数（包含模型引用、系统提示词与思考级别）
     * @param eventSink 用于广播流式增量事件的接收接收器
     * @return 异步生成的助手完整消息 Future
     */
    CompletableFuture<AssistantMessage> call(AgentContext context, AgentLoopConfig config, AgentEventSink eventSink);
}
