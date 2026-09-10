package com.claubloom.harness.core.loop;

import com.claubloom.harness.core.tool.ToolContext;
import com.claubloom.harness.core.tool.ToolDefinition;
import com.claubloom.harness.core.tool.ToolExecutor;
import com.claubloom.harness.core.tool.ToolRegistry;
import com.claubloom.harness.protocol.content.TextContent;
import com.claubloom.harness.protocol.content.ToolCallContent;
import com.claubloom.harness.protocol.message.AgentMessage;
import com.claubloom.harness.protocol.message.AssistantMessage;
import com.claubloom.harness.protocol.message.UserMessage;
import com.claubloom.harness.protocol.tool.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentLoop 中止机制回归测试：点击前端"中止"后，循环必须在下一个边界立即停止——
 * 不再发起新的 LLM 调用、不再执行工具，并通过在途调用取消即时中断流式输出。
 * 修复前 {@code abort()} 仅重置会话 phase，循环对此毫无感知，会一直执行到自然结束。
 */
public class AgentLoopAbortTest {

    /* ---------- 测试脚手架 ---------- */

    /** 可编程中止令牌：复刻运行时 abort() 的语义（置位 + 取消在途调用） */
    private static final class TestAbortHandle implements TurnAbortHandle {
        private final AtomicBoolean aborted = new AtomicBoolean(false);
        private volatile CompletableFuture<AssistantMessage> active;

        void abort() {
            aborted.set(true);
            CompletableFuture<AssistantMessage> current = active;
            if (current != null) {
                current.cancel(true);
            }
        }

        @Override
        public boolean isAborted() {
            return aborted.get();
        }

        @Override
        public void track(CompletableFuture<AssistantMessage> call) {
            active = call;
            // 运行时语义：登记在途调用时若已请求中止，立即取消该调用
            if (call != null && aborted.get()) {
                call.cancel(true);
            }
        }
    }

    /**
     * 按脚本执行行为的 LlmCaller 桩。行为收到中止令牌，可决定是否先 abort 再返回；
     * 返回 {@code null} 表示产生一个永不完成的在途调用（模拟尚未结束的流式请求）。
     */
    private static final class StubLlmCaller implements LlmCaller {
        private final List<Function<TestAbortHandle, AssistantMessage>> behaviors;
        private final AtomicInteger calls = new AtomicInteger();

        private StubLlmCaller(List<Function<TestAbortHandle, AssistantMessage>> behaviors) {
            this.behaviors = behaviors;
        }

        @Override
        public CompletableFuture<AssistantMessage> call(AgentContext context, AgentLoopConfig config,
                                                        AgentEventSink eventSink) {
            int index = calls.getAndIncrement();
            TestAbortHandle handle = config.getAbortHandle() instanceof TestAbortHandle h ? h : null;
            AssistantMessage message = behaviors.get(Math.min(index, behaviors.size() - 1)).apply(handle);
            if (message == null) {
                return new CompletableFuture<>();
            }
            return CompletableFuture.completedFuture(message);
        }
    }

    private static AssistantMessage textResponse(String text) {
        return AssistantMessage.complete("asst-" + java.util.UUID.randomUUID(),
                List.of(new TextContent(text)), null, null, null, System.currentTimeMillis(), "stop");
    }

    private static AssistantMessage toolCallResponse(String toolCallId) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("text", "hi");
        return AssistantMessage.complete("asst-" + java.util.UUID.randomUUID(),
                List.of(new ToolCallContent(toolCallId, "echo", input)),
                null, null, null, System.currentTimeMillis(), "toolUse");
    }

    private static ToolRegistry echoRegistry(AtomicInteger executions) {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolDefinition() {
            @Override
            public String name() {
                return "echo";
            }

            @Override
            public String description() {
                return "Echoes the given text";
            }

            @Override
            public Map<String, Object> parameterSchema() {
                return Map.of("type", "object");
            }

            @Override
            public CompletableFuture<ToolResult> execute(ToolContext context, Map<String, Object> arguments) {
                executions.incrementAndGet();
                return CompletableFuture.completedFuture(
                        ToolResult.success(String.valueOf(arguments.getOrDefault("text", ""))));
            }
        });
        return registry;
    }

    private static List<AgentMessage> run(AgentLoopConfig loopConfig, LlmCaller caller,
                                          AtomicInteger toolExecutions) throws Exception {
        AgentLoop loop = new AgentLoop(new ToolExecutor(echoRegistry(toolExecutions)));
        List<AgentMessage> prompts = List.of(UserMessage.text("开始任务"));
        AgentContext context = new AgentContext("session-abort-test", "/tmp", new ArrayList<>());
        return loop.runAgentLoop(prompts, context, loopConfig, event -> { }, caller)
                .get(15, TimeUnit.SECONDS);
    }

    private static AgentLoopConfig configWithHandle(TestAbortHandle handle) {
        return AgentLoopConfig.builder().tools(List.of()).abortHandle(handle).build();
    }

    /* ---------- 用例 ---------- */

    @Test
    @DisplayName("循环开始前已请求中止：不发起任何 LLM 调用，直接写入中止标记")
    void should_stopBeforeAnyCall_when_abortedUpfront() throws Exception {
        TestAbortHandle handle = new TestAbortHandle();
        handle.abort();
        StubLlmCaller caller = new StubLlmCaller(List.of(h -> textResponse("不应被调用")));

        List<AgentMessage> produced = run(configWithHandle(handle), caller, new AtomicInteger());

        assertThat(caller.calls.get()).as("中止后不得发起 LLM 调用").isZero();
        assertThat(produced).hasSize(2);
        AssistantMessage marker = (AssistantMessage) produced.get(1);
        assertThat(marker.status()).isEqualTo("aborted");
        assertThat(marker.stopReason()).isEqualTo("aborted");
    }

    @Test
    @DisplayName("推理完成后收到中止请求：不执行本轮工具调用、不再发起新调用")
    void should_discardPendingTools_when_abortAfterResponse() throws Exception {
        TestAbortHandle handle = new TestAbortHandle();
        AtomicInteger toolExecutions = new AtomicInteger();
        StubLlmCaller caller = new StubLlmCaller(List.of(
                h -> toolCallResponse("call_1"),
                // 第二次调用期间用户点击中止：消息已正常返回，但循环必须就此停住
                h -> {
                    h.abort();
                    return textResponse("第二轮回复");
                }
        ));

        List<AgentMessage> produced = run(configWithHandle(handle), caller, toolExecutions);

        assertThat(caller.calls.get()).as("中止后不得再发起下一轮 LLM 调用").isEqualTo(2);
        assertThat(toolExecutions.get()).as("首轮工具应已执行").isEqualTo(1);
        assertThat(produced).hasSize(4);
        AssistantMessage marker = (AssistantMessage) produced.get(3);
        assertThat(marker.status()).isEqualTo("aborted");
        assertThat(marker.content()).anyMatch(c -> c instanceof TextContent tc && tc.text().contains("中止"));
    }

    @Test
    @DisplayName("在途 LLM 调用被中止取消时应立即退出循环（流式输出即时中断）")
    void should_stopImmediately_when_inFlightCallCancelled() throws Exception {
        TestAbortHandle handle = new TestAbortHandle();
        AtomicInteger toolExecutions = new AtomicInteger();
        StubLlmCaller caller = new StubLlmCaller(List.of(
                // 调用发起后用户点击中止：在途调用被 cancel，join 抛出 CancellationException
                h -> {
                    h.abort();
                    return null;
                }
        ));

        List<AgentMessage> produced = run(configWithHandle(handle), caller, toolExecutions);

        assertThat(caller.calls.get()).isEqualTo(1);
        assertThat(toolExecutions.get()).isZero();
        assertThat(produced).hasSize(2);
        AssistantMessage marker = (AssistantMessage) produced.get(1);
        assertThat(marker.status()).isEqualTo("aborted");
    }
}
