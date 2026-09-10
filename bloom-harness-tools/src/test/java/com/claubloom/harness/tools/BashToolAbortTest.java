package com.claubloom.harness.tools;

import com.claubloom.harness.core.loop.TurnAbortHandle;
import com.claubloom.harness.core.tool.ToolContext;
import com.claubloom.harness.core.tool.ToolDefinition;
import com.claubloom.harness.protocol.tool.ToolResult;
import com.claubloom.harness.tools.builtin.BashTool;
import com.claubloom.harness.tools.sandbox.PathSandbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BashTool 中止能力回归测试：用户点击"终止"时，正在长时间运行的命令必须在
 * 轮询分片内被检测到并强杀进程树，而不是等命令自然结束（此前 waitFor 全程阻塞，
 * 最长 10 分钟内中止形同虚设）。
 */
public class BashToolAbortTest {

    @TempDir
    Path tempWorkspace;

    /** 在第 N 次轮询后进入中止状态的令牌（每次 waitFor 分片触发一次轮询） */
    private static final class DelayedAbortHandle implements TurnAbortHandle {
        private final AtomicInteger polls = new AtomicInteger();
        private final int abortAfterPolls;
        private final AtomicBoolean aborted = new AtomicBoolean(false);

        private DelayedAbortHandle(int abortAfterPolls) {
            this.abortAfterPolls = abortAfterPolls;
        }

        @Override
        public boolean isAborted() {
            if (aborted.get()) {
                return true;
            }
            if (polls.incrementAndGet() >= abortAfterPolls) {
                aborted.set(true);
            }
            return aborted.get();
        }

        @Override
        public void track(CompletableFuture<com.claubloom.harness.protocol.message.AssistantMessage> activeCall) {
            // 工具层测试不涉及 LLM 调用登记
        }
    }

    @Test
    @DisplayName("bash 执行期间收到中止请求：进程树被强杀，工具立即返回 aborted 错误结果")
    void should_killProcessAndReturnAborted_when_abortRequestedDuringExecution() throws Exception {
        BashTool bashTool = new BashTool(new PathSandbox(tempWorkspace));
        // 第 3 次轮询（约 200~300ms）后进入中止状态，被测命令 sleep 30 远长于该延迟
        ToolContext context = new ToolContext(
                "session-abort", tempWorkspace.toString(), null, null, new DelayedAbortHandle(3));

        long start = System.currentTimeMillis();
        ToolResult result = bashTool.execute(context, Map.of("command", "sleep 30"))
                .get(10, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result.isError()).as("中止应表现为错误结果").isTrue();
        assertThat(String.valueOf(result.content())).contains("[aborted by user]");
        assertThat(elapsed).as("中止应在轮询分片级延迟内生效，而不是等 sleep 30 结束")
                .isLessThan(10_000L);

        // 进程必须已被终止：30 秒 sleep 不可能自然结束
        assertThat(elapsed).isLessThan(30_000L);
    }

    @Test
    @DisplayName("未请求中止时长命令不受影响：waitFor 分片轮询不改变超时语义")
    void should_runNormally_when_noAbortRequested() throws Exception {
        BashTool bashTool = new BashTool(new PathSandbox(tempWorkspace));
        ToolContext context = new ToolContext(
                "session-normal", tempWorkspace.toString(), null, null, new DelayedAbortHandle(Integer.MAX_VALUE));

        ToolResult result = bashTool.execute(context, Map.of("command", "echo ok"))
                .get(10, TimeUnit.SECONDS);

        assertThat(result.isError()).isFalse();
        assertThat(String.valueOf(result.content())).contains("ok");
    }

    @Test
    @DisplayName("无中止令牌的旧调用方式保持兼容：命令正常执行")
    void should_stayCompatible_when_contextHasNoAbortHandle() throws Exception {
        BashTool bashTool = new BashTool(new PathSandbox(tempWorkspace));
        ToolDefinition tool = bashTool;
        ToolContext context = new ToolContext("session-legacy", tempWorkspace.toString(), null, null);

        ToolResult result = tool.execute(context, Map.of("command", "echo legacy"))
                .get(10, TimeUnit.SECONDS);

        assertThat(result.isError()).isFalse();
        assertThat(String.valueOf(result.content())).contains("legacy");
    }
}
