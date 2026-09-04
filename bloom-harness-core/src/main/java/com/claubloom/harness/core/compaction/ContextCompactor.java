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
 * ContextCompactor implements context window compaction and cut point discovery matching pi compaction.ts.
 */
@Slf4j
@Component
public class ContextCompactor {

    private static final int DEFAULT_RETAINED_TAIL_SIZE = 4;

    /**
     * Determine if context messages need compaction based on threshold.
     *
     * @param totalEstimatedTokens current token count
     * @param contextWindow max tokens allowed
     * @param threshold compaction trigger threshold (e.g. 0.8 for 80%)
     */
    public boolean shouldCompact(int totalEstimatedTokens, int contextWindow, double threshold) {
        if (contextWindow <= 0) {
            return false;
        }
        return (double) totalEstimatedTokens / (double) contextWindow >= threshold;
    }

    /**
     * Find a safe cut point in the message history so that tool calls and tool results stay paired.
     *
     * @param messages entire message list
     * @param retainedTailSize number of recent messages to retain in full
     * @return preparation object with slice indices
     */
    public Optional<CompactionPreparation> prepareCompaction(List<AgentMessage> messages, int retainedTailSize) {
        if (messages == null || messages.size() <= (retainedTailSize <= 0 ? DEFAULT_RETAINED_TAIL_SIZE : retainedTailSize)) {
            return Optional.empty();
        }

        int tailSize = retainedTailSize > 0 ? retainedTailSize : DEFAULT_RETAINED_TAIL_SIZE;
        int targetCutPoint = messages.size() - tailSize;

        // Ensure we don't cut in the middle of a ToolCall / ToolResult sequence
        int safeCutPoint = targetCutPoint;
        while (safeCutPoint > 0) {
            AgentMessage msg = messages.get(safeCutPoint);
            // If the message at cutPoint is a ToolResultMessage, move cutPoint before its AssistantMessage
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
     * Rough token estimation (4 chars ~ 1 token heuristic).
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
