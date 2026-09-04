package com.claubloom.harness.core.loop;

import com.claubloom.harness.core.tool.ToolDefinition;
import com.claubloom.harness.core.tool.ToolExecutionMode;
import com.claubloom.harness.protocol.message.AgentMessage;
import com.claubloom.harness.protocol.message.AssistantMessage;
import com.claubloom.harness.protocol.model.ModelRef;
import com.claubloom.harness.protocol.session.ThinkingLevel;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 控制智能体循环（Agent Loop）执行策略与生命周期的配置选项。
 */
@Data
@Builder
public class AgentLoopConfig {

    @Builder.Default
    private int maxTurns = 50;

    @Builder.Default
    private double compactionThreshold = 0.8;

    private ModelRef model;

    @Builder.Default
    private ThinkingLevel thinkingLevel = ThinkingLevel.OFF;

    private String systemPrompt;

    @Builder.Default
    private ToolExecutionMode toolExecutionMode = ToolExecutionMode.SEQUENTIAL;

    private List<ToolDefinition> tools;

    private Supplier<List<AgentMessage>> getSteeringMessages;

    private Function<AgentContext, NextTurnSnapshot> prepareNextTurn;

    public record NextTurnSnapshot(
            AgentContext context,
            ModelRef model,
            ThinkingLevel thinkingLevel
    ) {
    }
}
