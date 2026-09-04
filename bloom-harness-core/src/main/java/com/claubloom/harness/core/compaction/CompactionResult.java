package com.claubloom.harness.core.compaction;

import com.claubloom.harness.protocol.message.AgentMessage;
import com.claubloom.harness.protocol.session.Usage;
import java.util.List;

/**
 * 上下文压缩结果，包含生成的新历史摘要与保留的最近关键消息。
 */
public record CompactionResult(
        String summary,
        int tokensBefore,
        List<AgentMessage> retainedTail,
        Usage usage
) {
}
