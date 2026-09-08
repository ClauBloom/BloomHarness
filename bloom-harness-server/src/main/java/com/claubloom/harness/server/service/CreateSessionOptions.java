package com.claubloom.harness.server.service;

import com.claubloom.harness.protocol.model.ModelRef;
import com.claubloom.harness.protocol.session.ThinkingLevel;
import lombok.Builder;

/**
 * 创建持久化会话的选项。
 * id 由服务器分配且具有抗碰撞性；服务实现必须持久化这个确切的 id。
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
