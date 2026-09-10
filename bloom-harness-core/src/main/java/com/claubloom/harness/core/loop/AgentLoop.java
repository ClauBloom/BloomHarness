package com.claubloom.harness.core.loop;

import com.claubloom.harness.core.event.*;
import com.claubloom.harness.core.tool.ToolContext;
import com.claubloom.harness.core.tool.ToolExecutor;
import com.claubloom.harness.protocol.content.TextContent;
import com.claubloom.harness.protocol.content.ToolCallContent;
import com.claubloom.harness.protocol.message.AgentMessage;
import com.claubloom.harness.protocol.message.AssistantMessage;
import com.claubloom.harness.protocol.message.ToolResultMessage;
import com.claubloom.harness.protocol.message.UserMessage;
import com.claubloom.harness.protocol.tool.ToolCall;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CancellationException;
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

    /** 空响应（无文本/无工具调用）时，同一上下文的立即重试次数 */
    private static final int MAX_EMPTY_RESPONSE_RETRIES = 1;

    /** 空响应重试耗尽后，注入续跑提示的最大次数；连续超过该次数仍为空则放弃并给出可见错误 */
    private static final int MAX_EMPTY_RESPONSE_NUDGES = 2;

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
        int maxTurns = config.getMaxTurns() > 0 ? config.getMaxTurns() : 200;

        List<AgentMessage> pendingMessages = config.getGetSteeringMessages() != null
                ? new ArrayList<>(config.getGetSteeringMessages().get())
                : new ArrayList<>();

        boolean hasMoreToolCalls = true;
        int consecutiveEmptyNudges = 0;
        boolean aborted = false;
        TurnAbortHandle abortHandle = config.getAbortHandle();

        while ((hasMoreToolCalls || !pendingMessages.isEmpty()) && turnCount < maxTurns) {
            turnCount++;

            // 中止检查点 1：用户已请求中止，立即退出循环（不再发起新的推理轮次）
            if (abortHandle != null && abortHandle.isAborted()) {
                log.info("Session {}: abort requested, stopping agent loop at turn boundary",
                        currentContext.getSessionId());
                aborted = true;
                break;
            }

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

            // 为助手轮次调用 LLM（空响应自动重试与续跑提示，见 callLlm）
            LlmCallOutcome outcome = callLlm(currentContext, config, emit, llmCaller, abortHandle);
            if (outcome.aborted()) {
                aborted = true;
                break;
            }
            AssistantMessage assistantMessage = outcome.message();
            if (assistantMessage == null) {
                // 空响应重试耗尽：注入一条续跑提示进入下一轮，给模型一次从中断处恢复的机会
                consecutiveEmptyNudges++;
                if (consecutiveEmptyNudges > MAX_EMPTY_RESPONSE_NUDGES) {
                    log.error("Session {}: model kept returning empty responses after {} continuation nudges, aborting run",
                            currentContext.getSessionId(), MAX_EMPTY_RESPONSE_NUDGES);
                    AssistantMessage giveUp = AssistantMessage.error(
                            UUID.randomUUID().toString(),
                            List.of(new TextContent(
                                    "⚠️ **模型连续返回空响应，任务已中止。**\n\n自动重试与续跑提示均未生效，"
                                            + "可能是上游服务商临时故障或当前上下文触发了上游兼容性问题。"
                                            + "请发送“继续”重试，或在设置(⚙️)中检查/更换供应商与模型。")),
                            null, null, System.currentTimeMillis(), "模型连续返回空响应");
                    emit.emit(new MessageStartEvent(giveUp));
                    emit.emit(new MessageEndEvent(giveUp));
                    currentContext.addMessage(giveUp);
                    newMessages.add(giveUp);
                    emit.emit(new TurnEndEvent(giveUp, List.of()));
                    emit.emit(new AgentEndEvent(newMessages));
                    return;
                }
                UserMessage nudge = UserMessage.text(
                        "（系统提示：上一轮模型返回了空响应且自动重试无效。请从中断处继续完成任务；"
                                + "如果任务已经完成，请直接给出最终结论。）");
                pendingMessages.add(nudge);
                emit.emit(new TurnStartEvent());
                continue;
            }
            consecutiveEmptyNudges = 0;

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
                // 中止检查点 3：不再执行本轮工具调用（助手的工具调用意图随中止一并作废）
                if (abortHandle != null && abortHandle.isAborted()) {
                    log.info("Session {}: abort requested before tool execution, discarding {} pending tool calls",
                            currentContext.getSessionId(), toolCalls.size());
                    aborted = true;
                    break;
                }
                ToolContext toolContext = new ToolContext(
                        currentContext.getSessionId(),
                        currentContext.getCwd(),
                        currentContext,
                        emit,
                        abortHandle
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

        // 中止退出：写入可见的中止标记（前端按 interrupted 样式渲染），保证对话记录完整可读
        if (aborted) {
            log.info("Session {}: agent loop stopped by user abort after {} turns", currentContext.getSessionId(), turnCount);
            AssistantMessage abortMarker = new AssistantMessage(
                    UUID.randomUUID().toString(),
                    "assistant",
                    List.of(new TextContent("⏹️ 任务已由用户中止。")),
                    null, null, null, System.currentTimeMillis(),
                    "aborted", "aborted", null);
            emit.emit(new MessageStartEvent(abortMarker));
            emit.emit(new MessageEndEvent(abortMarker));
            currentContext.addMessage(abortMarker);
            newMessages.add(abortMarker);
            emit.emit(new TurnEndEvent(abortMarker, List.of()));
            emit.emit(new AgentEndEvent(newMessages));
            return;
        }

        // 循环若因轮数上限耗尽而退出（仍有待执行的工具调用或待注入消息），
        // 必须给出用户可见的终止原因，而不是无声消失让人误以为任务完成
        if (hasMoreToolCalls || !pendingMessages.isEmpty()) {
            log.warn("Session {}: agent loop exhausted maxTurns ({}), stopping with visible notice",
                    currentContext.getSessionId(), maxTurns);
            AssistantMessage limitMessage = AssistantMessage.error(
                    UUID.randomUUID().toString(),
                    List.of(new TextContent(
                            "⚠️ **已达单次任务最大轮数 (" + maxTurns + ")，任务尚未完成。**\n\n"
                                    + "发送“继续”即可在新一轮执行中接着当前进度继续。")),
                    null, null, System.currentTimeMillis(), "已达最大轮数上限");
            emit.emit(new MessageStartEvent(limitMessage));
            emit.emit(new MessageEndEvent(limitMessage));
            currentContext.addMessage(limitMessage);
            newMessages.add(limitMessage);
            emit.emit(new TurnEndEvent(limitMessage, List.of()));
        }

        emit.emit(new AgentEndEvent(newMessages));
    }

    /** 单次"带韧性策略"的 LLM 调用结果：{@code aborted=true} 表示期间收到中止请求（message 恒为 null） */
    private record LlmCallOutcome(AssistantMessage message, boolean aborted) {
    }

    /**
     * 调用模型并对"空响应"做韧性处理，同时接入中止机制。
     * <p>
     * 中止：调用前轮询中止令牌；在途调用登记到令牌（供运行时 cancel 以中断流式输出），
     * 被 {@code cancel} 时 join 抛出 {@link CancellationException}，按中止处理。
     * <p>
     * 空响应韧性：上游偶尔会产出 HTTP 200 但零文本、零工具调用的空流——若把它当作最终答复，
     * 整个任务会被静默终止且不留任何痕迹（这正是"执行到一半突然停止"的主要成因）。
     * 策略：同一上下文立即重试 {@link #MAX_EMPTY_RESPONSE_RETRIES} 次；仍为空则返回
     * {@code message=null, aborted=false}，由调用方注入续跑提示进入下一轮。
     */
    private LlmCallOutcome callLlm(
            AgentContext context,
            AgentLoopConfig config,
            AgentEventSink emit,
            LlmCaller llmCaller,
            TurnAbortHandle abortHandle
    ) throws Exception {
        for (int attempt = 0; ; attempt++) {
            // 中止检查点 2a：发起新调用前
            if (abortHandle != null && abortHandle.isAborted()) {
                return new LlmCallOutcome(null, true);
            }
            CompletableFuture<AssistantMessage> callFuture = llmCaller.call(context, config, emit);
            if (abortHandle != null) {
                abortHandle.track(callFuture);
            }
            AssistantMessage message;
            try {
                message = callFuture.join();
            } catch (CancellationException e) {
                // 运行时 abort() 已取消在途调用，流式输出被即时中断
                log.info("Session {}: in-flight LLM call cancelled by abort", context.getSessionId());
                return new LlmCallOutcome(null, true);
            } finally {
                if (abortHandle != null) {
                    abortHandle.track(null);
                }
            }
            // 中止检查点 2b：调用已完成但期间收到中止请求——响应随中止一并作废，不写入记录
            if (abortHandle != null && abortHandle.isAborted()) {
                return new LlmCallOutcome(null, true);
            }
            if (!isEmptyResponse(message)) {
                return new LlmCallOutcome(message, false);
            }
            if (attempt < MAX_EMPTY_RESPONSE_RETRIES) {
                log.warn("Session {}: received empty assistant response (call attempt {}/{}), retrying immediately",
                        context.getSessionId(), attempt + 1, MAX_EMPTY_RESPONSE_RETRIES + 1);
                continue;
            }
            log.warn("Session {}: empty assistant responses persisted after {} calls, requesting continuation nudge",
                    context.getSessionId(), attempt + 1);
            return new LlmCallOutcome(null, false);
        }
    }

    /**
     * 判断是否为空响应：既无任何工具调用，也无任何非空白文本。
     * 仅含思考内容的响应同样视为空（思考内容不回传上下文，对用户不可见）。
     */
    private static boolean isEmptyResponse(AssistantMessage message) {
        if (message == null || message.content() == null || message.content().isEmpty()) {
            return true;
        }
        boolean hasText = false;
        boolean hasToolCall = false;
        for (var content : message.content()) {
            if (content instanceof ToolCallContent) {
                hasToolCall = true;
            } else if (content instanceof TextContent tc && tc.text() != null && !tc.text().isBlank()) {
                hasText = true;
            }
        }
        return !hasText && !hasToolCall;
    }
}
