package com.claubloom.harness.core.compaction;

import com.claubloom.harness.protocol.message.AgentMessage;
import com.claubloom.harness.protocol.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 上下文窗口压缩器：当令牌总数接近上下文窗口阈值时，
 * 在历史消息中查找安全切割点（避免拆分工具调用/结果对），分离出待摘要的前段与需保留的尾段。
 */
@Slf4j
@Component
public class ContextCompactor {

    private static final int DEFAULT_RETAINED_TAIL_SIZE = 4;

    /**
     * 根据阈值判断上下文消息是否需要进行压缩。
     *
     * @param totalEstimatedTokens 当前令牌总数
     * @param contextWindow 允许的最大令牌数
     * @param threshold 压缩触发阈值（例如 0.8 表示 80%）
     */
    public boolean shouldCompact(int totalEstimatedTokens, int contextWindow, double threshold) {
        if (contextWindow <= 0) {
            return false;
        }
        return (double) totalEstimatedTokens / (double) contextWindow >= threshold;
    }

    /**
     * 在消息历史中查找安全的切割点，使工具调用与工具结果保持成对。
     *
     * @param messages 完整消息列表
     * @param retainedTailSize 需要完整保留的最近消息条数
     * @return 携带切片索引的压缩准备对象
     */
    public Optional<CompactionPreparation> prepareCompaction(List<AgentMessage> messages, int retainedTailSize) {
        if (messages == null || messages.size() <= (retainedTailSize <= 0 ? DEFAULT_RETAINED_TAIL_SIZE : retainedTailSize)) {
            return Optional.empty();
        }

        int tailSize = retainedTailSize > 0 ? retainedTailSize : DEFAULT_RETAINED_TAIL_SIZE;
        int targetCutPoint = messages.size() - tailSize;

        // 确保不会在 ToolCall / ToolResult 序列中间进行切割
        int safeCutPoint = targetCutPoint;
        while (safeCutPoint > 0) {
            AgentMessage msg = messages.get(safeCutPoint);
            // 若 cutPoint 处的消息为 ToolResultMessage，则将 cutPoint 前移到其 AssistantMessage 之前
            if ("tool".equalsIgnoreCase(msg.role())) {
                safeCutPoint--;
            } else {
                break;
            }
        }

        if (safeCutPoint <= 0) {
            return Optional.empty();
        }

        List<AgentMessage> toSummarize = messages.subList(0, safeCutPoint);
        List<AgentMessage> tail = messages.subList(safeCutPoint, messages.size());

        int tokensBefore = estimateTokens(messages);

        return Optional.of(new CompactionPreparation(safeCutPoint, tokensBefore, new ArrayList<>(toSummarize), new ArrayList<>(tail)));
    }

    /**
     * 粗略的令牌估算（启发式：4 个字符约等于 1 个令牌）。
     */
    public int estimateTokens(List<AgentMessage> messages) {
        if (messages == null) {
            return 0;
        }
        int totalChars = 0;
        for (AgentMessage message : messages) {
            totalChars += message.toString().length();
        }
        return Math.max(1, totalChars / 4);
    }
}
