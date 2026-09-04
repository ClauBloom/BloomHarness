package com.claubloom.harness.server.service;

import com.claubloom.harness.protocol.model.ModelRef;
import com.claubloom.harness.protocol.session.ThinkingLevel;
import lombok.Builder;

/**
 * Options for creating a durable session.
 * Mirrors pi's CreateSessionOptions in packages/server/src/types.ts.
 * The id is collision-resistant and assigned by the server; the service must persist this exact id.
 */
@Builder
public record CreateSessionOptions(
        String id,
        String cwd,
        String name,
        ModelRef model,
        ThinkingLevel thinkingLevel
) {
}
