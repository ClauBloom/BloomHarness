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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentLoop 韧性回归测试：上游返回"空响应"（HTTP 200 但零文本/零工具调用）时，
 * 循环必须自动恢复或给出可见的终止原因，绝不允许静默终止任务。
 * 该场景对应真实事故：路由引擎把上游空流当作正常完成，导致任务执行到一半突然停止。
 */
public class AgentLoopResilienceTest {

    /* ---------- 测试脚手架 ---------- */

    /** 按脚本依次返回预设助手消息的 LlmCaller 桩 */
    private static final class ScriptedLlmCaller implements LlmCaller {
        private final List<AssistantMessage> script;
        private final AtomicInteger calls = new AtomicInteger();

        private ScriptedLlmCaller(List<AssistantMessage> script) {
            this.script = new ArrayList<>(script);
        }

        @Override
        public CompletableFuture<AssistantMessage> call(AgentContext context, AgentLoopConfig config,
                                                        AgentEventSink eventSink) {
            int index = calls.getAndIncrement();
            AssistantMessage next = index < script.size()
                    ? script.get(index)
                    : AssistantMessage.complete("unexpected-" + index, List.of(), null, null, null,
                            System.currentTimeMillis(), "stop");
            return CompletableFuture.completedFuture(next);
        }
    }

    /** 空响应助手消息：无文本、无工具调用（与线上事故中落库的空消息一致） */
    private static AssistantMessage emptyResponse() {
        return AssistantMessage.complete("empty-" + java.util.UUID.randomUUID(), List.of(),
                null, null, null, System.currentTimeMillis(), "stop");
    }

    /** 带最终文本的助手消息 */
    private static AssistantMessage textResponse(String text) {
        return AssistantMessage.complete("asst-" + java.util.UUID.randomUUID(),
                List.of(new TextContent(text)), null, null, null, System.currentTimeMillis(), "stop");
    }

    /** 调用一次 echo 工具的助手消息 */
    private static AssistantMessage toolCallResponse(String toolCallId) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("text", "hi");
        return AssistantMessage.complete("asst-" + java.util.UUID.randomUUID(),
                List.of(new ToolCallContent(toolCallId, "echo", input)),
                null, null, null, System.currentTimeMillis(), "toolUse");
    }

    private static ToolRegistry echoRegistry() {
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
                return CompletableFuture.completedFuture(
                        ToolResult.success(String.valueOf(arguments.getOrDefault("text", ""))));
            }
        });
        return registry;
    }

    private static AgentLoopConfig config(Supplier<List<AgentMessage>> steering, Integer maxTurns) {
        AgentLoopConfig.AgentLoopConfigBuilder builder = AgentLoopConfig.builder().tools(List.of());
        if (steering != null) {
            builder.getSteeringMessages(steering);
        }
        if (maxTurns != null) {
            builder.maxTurns(maxTurns);
        }
        return builder.build();
    }

    private static List<AgentMessage> run(AgentLoopConfig loopConfig, LlmCaller caller) throws Exception {
        AgentLoop loop = new AgentLoop(new ToolExecutor(echoRegistry()));
        List<AgentMessage> prompts = List.of(UserMessage.text("开始任务"));
        AgentContext context = new AgentContext("session-test", "/tmp", new ArrayList<>());
        return loop.runAgentLoop(prompts, context, loopConfig, event -> { }, caller)
                .get(15, TimeUnit.SECONDS);
    }

    /* ---------- 用例 ---------- */

    @Test
    @DisplayName("空响应后应自动重试并继续任务，空消息不得进入上下文（静默停止回归）")
    void should_retryAndContinue_when_firstResponseIsEmpty() throws Exception {
        ScriptedLlmCaller caller = new ScriptedLlmCaller(List.of(
                emptyResponse(),
                textResponse("任务完成")
        ));

        List<AgentMessage> produced = run(config(null, null), caller);

        assertThat(caller.calls.get()).as("空响应应触发立即重试").isEqualTo(2);
        assertThat(produced).hasSize(2);
        assertThat(produced.get(0)).isInstanceOf(UserMessage.class);
        AssistantMessage finalMessage = (AssistantMessage) produced.get(1);
        assertThat(finalMessage.status()).isEqualTo("complete");
        assertThat(finalMessage.content()).anyMatch(c -> c instanceof TextContent tc
                && tc.text().contains("任务完成"));
    }

    @Test
    @DisplayName("工具调用之后收到空响应时应恢复执行（本次事故的直接形态）")
    void should_recover_when_emptyResponseFollowsToolCalls() throws Exception {
        ScriptedLlmCaller caller = new ScriptedLlmCaller(List.of(
                toolCallResponse("call_1"),
                emptyResponse(),
                textResponse("工具结果已处理，任务完成")
        ));

        List<AgentMessage> produced = run(config(null, null), caller);

        // user + assistant(toolUse) + toolResult + final，空响应不得出现在结果中
        assertThat(caller.calls.get()).isEqualTo(3);
        assertThat(produced).hasSize(4);
        assertThat(produced.get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(produced.get(2)).isInstanceOf(
                com.claubloom.harness.protocol.message.ToolResultMessage.class);
        AssistantMessage finalMessage = (AssistantMessage) produced.get(3);
        assertThat(finalMessage.content()).anyMatch(c -> c instanceof TextContent tc
                && tc.text().contains("任务完成"));
        assertThat(produced).noneMatch(m -> m instanceof AssistantMessage am && am.content().isEmpty());
    }

    @Test
    @DisplayName("持续空响应时注入续跑提示，仍失败则给出可见错误卡片而非静默结束")
    void should_giveUpVisibly_when_modelKeepsReturningEmpty() throws Exception {
        ScriptedLlmCaller caller = new ScriptedLlmCaller(new ArrayList<>());

        List<AgentMessage> produced = run(config(null, null), caller);

        // user + nudge1 + nudge2 + giveUp 错误卡片
        assertThat(produced).hasSize(4);
        assertThat(produced.get(1)).isInstanceOf(UserMessage.class);
        assertThat(produced.get(2)).isInstanceOf(UserMessage.class);
        AssistantMessage giveUp = (AssistantMessage) produced.get(3);
        assertThat(giveUp.status()).isEqualTo("error");
        assertThat(giveUp.content()).anyMatch(c -> c instanceof TextContent tc
                && tc.text().contains("空响应"));
    }

    @Test
    @DisplayName("达到最大轮数上限时必须以可见提示终止，不得无声消失")
    void should_stopWithVisibleNotice_when_maxTurnsExhausted() throws Exception {
        // 每次查询都返回一条干预消息，驱动循环持续运行直至轮数耗尽
        Supplier<List<AgentMessage>> alwaysSteer = () -> List.of(UserMessage.text("继续"));
        ScriptedLlmCaller caller = new ScriptedLlmCaller(List.of(
                textResponse("第一轮"),
                textResponse("第二轮")
        ));

        List<AgentMessage> produced = run(config(alwaysSteer, 2), caller);

        AssistantMessage last = (AssistantMessage) produced.get(produced.size() - 1);
        assertThat(last.status()).isEqualTo("error");
        assertThat(last.content()).anyMatch(c -> c instanceof TextContent tc
                && tc.text().contains("最大轮数"));
    }
}
