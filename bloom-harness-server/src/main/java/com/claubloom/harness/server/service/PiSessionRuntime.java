package com.claubloom.harness.server.service;

import com.claubloom.harness.protocol.model.ModelRef;
import com.claubloom.harness.protocol.session.SessionPhase;
import com.claubloom.harness.protocol.session.SessionSnapshot;
import com.claubloom.harness.protocol.session.ThinkingLevel;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 单个已获取的持久化会话运行时接口。冲突操作必须立即拒绝而非排队阻塞。
 * 严格对齐 pi-agent 的 PiSessionRuntime 规范。
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
