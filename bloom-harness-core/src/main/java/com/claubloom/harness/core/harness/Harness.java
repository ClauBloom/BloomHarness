package com.claubloom.harness.core.harness;

import com.claubloom.harness.core.loop.AgentContext;
import com.claubloom.harness.core.loop.AgentEventSink;
import com.claubloom.harness.core.loop.AgentLoopConfig;
import com.claubloom.harness.protocol.message.AgentMessage;
import com.claubloom.harness.protocol.result.Result;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 驱动会话运行与资源集成的框架生命周期接口。
 */
public interface Harness {

    /**
     * 使用新的用户指令运行一个轮次。
     */
    CompletableFuture<Result<List<AgentMessage>>> run(
            List<AgentMessage> prompts,
            AgentContext context,
            AgentLoopConfig config,
            AgentEventSink eventSink
    );

    /**
     * 当前 Harness 实例的运行执行阶段。
     */
    HarnessPhase getPhase();

    /**
     * 取消或中断当前正在执行的活动任务。
     */
    void abort();
}
