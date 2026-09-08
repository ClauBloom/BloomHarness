package com.claubloom.harness.core.loop;

import com.claubloom.harness.core.event.*;
import com.claubloom.harness.core.tool.ToolContext;
import com.claubloom.harness.core.tool.ToolExecutor;
import com.claubloom.harness.protocol.content.ToolCallContent;
import com.claubloom.harness.protocol.message.AgentMessage;
import com.claubloom.harness.protocol.message.AssistantMessage;
import com.claubloom.harness.protocol.message.ToolResultMessage;
import com.claubloom.harness.protocol.tool.ToolCall;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 智能体推理循环：在 Java 21 虚拟线程中管理状态机流转与多轮 (Turn) 推理。
 * 每轮循环调用 LLM，若响应包含工具调用则执行工具并将结果注入上下文后继续下一轮，
 * 直至 LLM 给出无工具调用的最终响应或触发终止条件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentLoop {

    private final ToolExecutor toolExecutor;

    /**
     * 启动并运行智能体自主循环。
     *
     * @param prompts 触发本次运行的初始输入消息列表
     * @param context 当前会话上下文环境
     * @param config 循环运行策略与参数配置
     * @param emit 全生命周期事件广播接收器
     * @param llmCaller 大语言模型调用器
     * @return 本次循环运行中生成的所有新消息列表
     */
    public CompletableFuture<List<AgentMessage>> runAgentLoop(
            List<AgentMessage> prompts,
            AgentContext context,
            AgentLoopConfig config,
            AgentEventSink emit,
            LlmCaller llmCaller
    ) {
        return CompletableFuture.supplyAsync(() -> {
            List<AgentMessage> newMessages = new ArrayList<>(prompts);
            AgentContext currentContext = new AgentContext(
                    context.getSessionId(),
                    context.getCwd(),
                    new ArrayList<>(context.getMessages())
            );
            if (prompts != null) {
                currentContext.getMessages().addAll(prompts);
            }

            try {
                emit.emit(new AgentStartEvent());
                emit.emit(new TurnStartEvent());
                if (prompts != null) {
                    for (AgentMessage prompt : prompts) {
                        emit.emit(new MessageStartEvent(prompt));
                        emit.emit(new MessageEndEvent(prompt));
                    }
                }

                runLoopSync(currentContext, newMessages, config, emit, llmCaller);
            } catch (Exception e) {
                log.error("Error running agent loop", e);
                throw new RuntimeException("Agent loop execution error", e);
            }

            return newMessages;
        }, Thread::startVirtualThread);
    }

    /**
     * 同步虚拟线程循环逻辑。
     */
    private void runLoopSync(
            AgentContext currentContext,
            List<AgentMessage> newMessages,
            AgentLoopConfig config,
            AgentEventSink emit,
            LlmCaller llmCaller
    ) throws Exception {
        int turnCount = 0;
        int maxTurns = config.getMaxTurns() > 0 ? config.getMaxTurns() : 50;

        List<AgentMessage> pendingMessages = config.getGetSteeringMessages() != null
                ? new ArrayList<>(config.getGetSteeringMessages().get())
                : new ArrayList<>();

        boolean hasMoreToolCalls = true;

        while ((hasMoreToolCalls || !pendingMessages.isEmpty()) && turnCount < maxTurns) {
            turnCount++;

            // 注入待处理的中途干预消息
            if (!pendingMessages.isEmpty()) {
                for (AgentMessage msg : pendingMessages) {
                    emit.emit(new MessageStartEvent(msg));
                    emit.emit(new MessageEndEvent(msg));
                    currentContext.addMessage(msg);
                    newMessages.add(msg);
                }
                pendingMessages.clear();
            }

            // 为助手轮次调用 LLM
            AssistantMessage assistantMessage = llmCaller.call(currentContext, config, emit).join();
            emit.emit(new MessageStartEvent(assistantMessage));
            emit.emit(new MessageEndEvent(assistantMessage));
            currentContext.addMessage(assistantMessage);
            newMessages.add(assistantMessage);

            if ("error".equalsIgnoreCase(assistantMessage.status()) || "aborted".equalsIgnoreCase(assistantMessage.status())) {
                emit.emit(new TurnEndEvent(assistantMessage, List.of()));
                emit.emit(new AgentEndEvent(newMessages));
                return;
            }

            // 从助手消息中提取工具调用
            List<ToolCall> toolCalls = new ArrayList<>();
            if (assistantMessage.content() != null) {
                for (var content : assistantMessage.content()) {
                    if (content instanceof ToolCallContent tc) {
                        toolCalls.add(new ToolCall(tc.toolCallId(), tc.toolName(), tc.input()));
                    }
                }
            }

            List<ToolResultMessage> toolResults = new ArrayList<>();
            hasMoreToolCalls = false;

            if (!toolCalls.isEmpty()) {
                ToolContext toolContext = new ToolContext(
                        currentContext.getSessionId(),
                        currentContext.getCwd(),
                        currentContext,
                        emit
                );

                ToolExecutor.ExecutionBatchResult batchResult = toolExecutor
                        .executeToolCalls(toolCalls, toolContext, config.getToolExecutionMode())
                        .join();

                toolResults.addAll(batchResult.messages());
                hasMoreToolCalls = !batchResult.terminate();

                for (ToolResultMessage tr : toolResults) {
                    emit.emit(new MessageStartEvent(tr));
                    emit.emit(new MessageEndEvent(tr));
                    currentContext.addMessage(tr);
                    newMessages.add(tr);
                }
            }

            emit.emit(new TurnEndEvent(assistantMessage, toolResults));

            // 检查工具执行期间用户是否提供了中途干预消息
            if (config.getGetSteeringMessages() != null) {
                List<AgentMessage> steer = config.getGetSteeringMessages().get();
                if (steer != null && !steer.isEmpty()) {
                    pendingMessages.addAll(steer);
                }
            }

            // 准备下一轮次（压缩检查或模型切换）
            if (hasMoreToolCalls || !pendingMessages.isEmpty()) {
                if (config.getPrepareNextTurn() != null) {
                    var snapshot = config.getPrepareNextTurn().apply(currentContext);
                    if (snapshot != null) {
                        if (snapshot.context() != null) {
                            currentContext = snapshot.context();
                        }
                    }
                }
                emit.emit(new TurnStartEvent());
            }
        }

        emit.emit(new AgentEndEvent(newMessages));
    }
}
