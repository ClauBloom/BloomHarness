package com.claubloom.harness.core.compaction;

import com.claubloom.harness.protocol.message.AgentMessage;
import java.util.List;

/**
 * 为生成摘要而准备的上下文切片。
 */
public record CompactionPreparation(
        int cutPointIndex,
        int tokensBefore,
        List<AgentMessage> messagesToSummarize,
        List<AgentMessage> retainedTail
) {
}
