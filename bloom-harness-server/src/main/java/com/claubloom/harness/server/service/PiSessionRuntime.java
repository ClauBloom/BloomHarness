package com.claubloom.harness.server.service;

import com.claubloom.harness.protocol.model.ModelRef;
import com.claubloom.harness.protocol.session.SessionPhase;
import com.claubloom.harness.protocol.session.SessionSnapshot;
import com.claubloom.harness.protocol.session.ThinkingLevel;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 单个已获取的持久化会话运行时接口。
 * 提供提示词执行、中途干预、中断、模型/思考级别切换与快照查询。
 * 冲突操作必须立即拒绝而非排队阻塞。
 */
public interface PiSessionRuntime {

    SessionSnapshot snapshot();

    SessionPhase getPhase();

    void prompt(String text);

    void steer(String text);

    void abort();

    void setModel(ModelRef model);

    void setThinking(ThinkingLevel thinkingLevel);

    void setCwd(String newCwd);

    Runnable subscribe(Consumer<PiSessionRuntimeEvent> listener);

    void dispose();
}
