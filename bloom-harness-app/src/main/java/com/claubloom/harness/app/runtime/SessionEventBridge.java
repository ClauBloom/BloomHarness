package com.claubloom.harness.app.runtime;

import com.claubloom.harness.core.event.AgentEvent;
import com.claubloom.harness.core.event.MessageEndEvent;
import com.claubloom.harness.core.event.MessageStartEvent;
import com.claubloom.harness.core.event.MessageUpdateEvent;
import com.claubloom.harness.core.event.ToolCallEvent;
import com.claubloom.harness.core.event.ToolResultEvent;
import com.claubloom.harness.core.loop.AgentEventSink;
import com.claubloom.harness.protocol.content.ToolCallContent;
import com.claubloom.harness.protocol.message.AgentMessage;
import com.claubloom.harness.protocol.message.AssistantMessage;
import com.claubloom.harness.protocol.message.ToolResultMessage;
import com.claubloom.harness.protocol.stream.TranscriptProgress;
import com.claubloom.harness.server.stream.SessionEventBroadcaster;
import lombok.extern.slf4j.Slf4j;

/**
 * 将核心智能体事件桥接转换为协议层的 TranscriptProgress：
 * 负责持久化对话记录到数据库，并实时向 SSE 订阅方分发进度事件。
 */
@Slf4j
public class SessionEventBridge implements AgentEventSink {

    private final String sessionId;
    private final TranscriptPersistence persistence;
    private final SessionEventBroadcaster broadcaster;

    public interface TranscriptPersistence {
        void append(String sessionId, String type, AgentMessage message);
    }

    public SessionEventBridge(
            String sessionId, TranscriptPersistence persistence, SessionEventBroadcaster broadcaster) {
        this.sessionId = sessionId;
        this.persistence = persistence;
        this.broadcaster = broadcaster;
    }

    @Override
    public void emit(AgentEvent event) throws Exception {
        if (event instanceof MessageStartEvent startEvent) {
            AgentMessage message = startEvent.message();
            if (message instanceof AssistantMessage assistant) {
                persistence.append(sessionId, "assistant_message", assistant);
                broadcaster.publish(sessionId, new TranscriptProgress.ItemStarted(assistant));
            }
        } else if (event instanceof MessageUpdateEvent update) {
            broadcaster.publish(sessionId, new TranscriptProgress.AssistantDelta(
                    update.messageId(), update.contentIndex(), update.kind(), update.delta()));
        } else if (event instanceof ToolCallEvent toolCallEvent) {
            ToolCallContent content = new ToolCallContent(
                    toolCallEvent.toolCall().toolCallId(),
                    toolCallEvent.toolCall().toolName(),
                    toolCallEvent.toolCall().input());
            AssistantMessage callMessage = AssistantMessage.complete(
                    toolCallEvent.toolCall().toolCallId(),
                    java.util.List.of(content),
                    null, null, null, System.currentTimeMillis(), "toolUse");
            broadcaster.publish(sessionId, new TranscriptProgress.ItemUpdated(callMessage));
        } else if (event instanceof ToolResultEvent resultEvent) {
            ToolResultMessage resultMessage = ToolResultMessage.success(
                    resultEvent.toolCall().toolCallId() + ":result",
                    resultEvent.toolCall().toolCallId(),
                    resultEvent.toolCall().toolName(),
                    resultEvent.toolCall().input(),
                    resultEvent.result().content(),
                    resultEvent.result().details(),
                    resultEvent.result().usage(),
                    System.currentTimeMillis());
            persistence.append(sessionId, "tool_result", resultMessage);
            broadcaster.publish(sessionId, new TranscriptProgress.ItemFinished(resultMessage));
        } else if (event instanceof MessageEndEvent endEvent) {
            // Message end for assistant items is covered by persistence above;
            // tool results are persisted on their ToolResultEvent.
        }
    }

    /** 将会话当前轮次的 SSE 事件流标记为正常完成结束。 */
    public void completeStream() {
        broadcaster.complete(sessionId, null);
    }

    public void failStream(Throwable error) {
        broadcaster.complete(sessionId, error);
    }
}
