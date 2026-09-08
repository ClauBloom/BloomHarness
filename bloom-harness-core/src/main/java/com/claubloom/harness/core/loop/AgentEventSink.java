package com.claubloom.harness.core.loop;

import com.claubloom.harness.core.event.AgentEvent;

/**
 * 用于接收核心智能体事件的异步或同步消费者接口。
 */
@FunctionalInterface
public interface AgentEventSink {

    /**
     * 发出一个智能体生命周期事件。
     *
     * @param event 要发出的事件
     * @throws Exception 若发送失败则抛出异常
     */
    void emit(AgentEvent event) throws Exception;
}
