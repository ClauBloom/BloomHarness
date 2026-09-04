package com.claubloom.harness.core.tool;

import com.claubloom.harness.core.event.ToolCallEvent;
import com.claubloom.harness.core.event.ToolResultEvent;
import com.claubloom.harness.protocol.content.MessageContent;
import com.claubloom.harness.protocol.content.TextContent;
import com.claubloom.harness.protocol.message.ToolResultMessage;
import com.claubloom.harness.protocol.tool.ToolCall;
import com.claubloom.harness.protocol.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * ToolExecutor 负责协调工具的调用与执行，支持顺序执行 (Sequential) 与并行执行 (Parallel) 两种执行模式。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutor {

    private final ToolRegistry toolRegistry;

    /**
     * 工具批量执行结果，包含产出的消息列表与是否提前终止循环的标志。
     */
    public record ExecutionBatchResult(
            List<ToolResultMessage> messages,
            boolean terminate
    ) {
    }

    /**
     * 批量执行从 Assistant 消息中解析出的工具调用集合。
     *
     * @param toolCalls 待执行的工具调用列表
     * @param toolContext 工具执行上下文
     * @param mode 执行模式（顺序或并行）
     * @return 批量执行结果的异步 Future
     */
    public CompletableFuture<ExecutionBatchResult> executeToolCalls(
            List<ToolCall> toolCalls,
            ToolContext toolContext,
            ToolExecutionMode mode
    ) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return CompletableFuture.completedFuture(new ExecutionBatchResult(List.of(), false));
        }

        if (mode == ToolExecutionMode.PARALLEL) {
            return executeParallel(toolCalls, toolContext);
        } else {
            return executeSequential(toolCalls, toolContext);
        }
    }

    private CompletableFuture<ExecutionBatchResult> executeSequential(
            List<ToolCall> toolCalls,
            ToolContext toolContext
    ) {
        CompletableFuture<List<ToolResultMessage>> future = CompletableFuture.completedFuture(new ArrayList<>());
        for (ToolCall call : toolCalls) {
            future = future.thenCompose(list ->
                    executeSingleCall(call, toolContext).thenApply(resultMessage -> {
                        list.add(resultMessage);
                        return list;
                    })
            );
        }
        return future.thenApply(messages -> new ExecutionBatchResult(messages, false));
    }

    private CompletableFuture<ExecutionBatchResult> executeParallel(
            List<ToolCall> toolCalls,
            ToolContext toolContext
    ) {
        List<CompletableFuture<ToolResultMessage>> futures = toolCalls.stream()
                .map(call -> executeSingleCall(call, toolContext))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<ToolResultMessage> messages = futures.stream()
                            .map(CompletableFuture::join)
                            .toList();
                    return new ExecutionBatchResult(messages, false);
                });
    }

    /**
     * Execute a single tool call with event notifications.
     */
    public CompletableFuture<ToolResultMessage> executeSingleCall(ToolCall call, ToolContext toolContext) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (toolContext.eventSink() != null) {
                    toolContext.eventSink().emit(new ToolCallEvent(call));
                }
            } catch (Exception e) {
                log.warn("Failed to emit ToolCallEvent for call: {}", call.toolCallId(), e);
            }
            return call;
        }).thenCompose(c -> {
            Optional<ToolDefinition> toolOpt = toolRegistry.find(c.toolName());
            if (toolOpt.isEmpty()) {
                ToolResult errorResult = ToolResult.error("Unknown tool: " + c.toolName());
                return CompletableFuture.completedFuture(errorResult);
            }
            ToolDefinition tool = toolOpt.get();
            @SuppressWarnings("unchecked")
            Map<String, Object> args = c.input() instanceof Map ? (Map<String, Object>) c.input() : Collections.emptyMap();
            return tool.execute(toolContext, args).exceptionally(ex ->
                    ToolResult.error("Tool execution failed with exception: " + ex.getMessage())
            );
        }).thenApply(result -> {
            try {
                if (toolContext.eventSink() != null) {
                    toolContext.eventSink().emit(new ToolResultEvent(call, result));
                }
            } catch (Exception e) {
                log.warn("Failed to emit ToolResultEvent for call: {}", call.toolCallId(), e);
            }

            long now = System.currentTimeMillis();
            return new ToolResultMessage(
                    UUID.randomUUID().toString(),
                    call.toolCallId(),
                    call.toolName(),
                    call.input(),
                    result.content(),
                    result.details(),
                    result.usage(),
                    now,
                    result.isError() ? "error" : "complete",
                    result.isError()
            );
        });
    }
}
