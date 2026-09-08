package com.claubloom.harness.server.service;

import com.claubloom.harness.protocol.model.ModelRef;
import com.claubloom.harness.protocol.session.ThinkingLevel;
import lombok.Builder;

/**
 * 创建持久化会话的选项。
 * 对齐 pi 在 packages/server/src/types.ts 中的 CreateSessionOptions。
 * id 具有抗碰撞性且由服务器分配；服务必须持久化这个确切的 id。
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
