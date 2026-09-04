package com.claubloom.harness.core.loop;

import com.claubloom.harness.core.event.AgentEvent;

/**
 * Asynchronous or synchronous consumer interface for receiving core Agent events.
 */
@FunctionalInterface
public interface AgentEventSink {

    /**
     * Emit an Agent lifecycle event.
     *
     * @param event the event to emit
     * @throws Exception if emission fails
     */
    void emit(AgentEvent event) throws Exception;
}
