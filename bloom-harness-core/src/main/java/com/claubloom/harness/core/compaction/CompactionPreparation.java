package com.claubloom.harness.core.compaction;

import com.claubloom.harness.protocol.message.AgentMessage;
import java.util.List;

/**
 * Context slice prepared for summarization.
 */
public record CompactionPreparation(
        int cutPointIndex,
        int tokensBefore,
        List<AgentMessage> messagesToSummarize,
        List<AgentMessage> retainedTail
) {
}
