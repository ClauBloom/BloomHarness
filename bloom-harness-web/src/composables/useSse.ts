import { useAgentStore } from '@/stores/agentStore';
import { useUiStore } from '@/stores/uiStore';

// 模块级单例状态：无论多少组件调用 useSession/useSse，全局有且仅维持一条活跃的 SSE 连接
let globalEventSource: EventSource | null = null;
let activeSessionId: string | null = null;

export function useSse() {
  const store = useAgentStore();
  const ui = useUiStore();

  function connectSse(sessionId: string) {
    if (!sessionId) return;

    // 已存在对当前会话的活跃连接时避免重复创建
    if (globalEventSource && activeSessionId === sessionId) {
      if (globalEventSource.readyState === EventSource.OPEN || globalEventSource.readyState === EventSource.CONNECTING) {
        return;
      }
    }

    closeSse();

    activeSessionId = sessionId;
    const es = new EventSource(`/api/stream/${sessionId}`);
    globalEventSource = es;

    es.onopen = () => {
      if (globalEventSource !== es) return;
      store.isConnected = true;
      ui.reportBackendOk();
    };

    es.onmessage = (event) => {
      if (globalEventSource !== es) return;
      try {
        const data = JSON.parse(event.data);
        if (data.type === 'chunk' && data.payload) {
          const payload = data.payload;
          if (payload.type === 'assistant_delta') {
            if (!store.isRunning) store.setPhase('turn');
            store.appendDelta(payload.kind, payload.delta);
          } else if (payload.type === 'item_started' || payload.type === 'item_updated' || payload.type === 'item_finished') {
            // 后端在 LLM 调用完成后才发布完整的 assistant 消息：此时流式缓冲已由该消息承载，
            // 先清空缓冲再落项，避免多步轮次中已完成文本与缓冲重复显示。
            if (payload.type === 'item_started' && payload.item?.role === 'assistant') {
              store.flushStreaming();
            }
            store.updateItem(payload.item);
          }
        } else if (data.type === 'error') {
          console.error('[SSE Error Event]', data);
          let msg = data.message || '模型执行过程出现异常';
          if (/Agent loop execution error/i.test(msg) || /尚未配置或选择/i.test(msg)) {
            msg = '尚未配置或选择 AI 模型，请点击设置(⚙️)配置服务商与模型。';
          }
          store.setError(msg);
          // 在当前对话尾部插入错误项，以便在消息流内直接展示 TurnErrorRow
          store.updateItem({
            id: `err-${Date.now()}`,
            role: 'assistant',
            timestamp: Date.now(),
            status: 'error',
            errorMessage: msg,
            content: [],
          });
          store.flushStreaming();
          store.setPhase('idle');
          closeSse();
        } else if (data.type === 'end') {
          store.flushStreaming();
          store.setPhase('idle');
          // 服务端已终结本轮流：主动关闭，避免浏览器在 Flux 结束后无效自动重连
          closeSse();
          // 同步最新全量状态
          fetch(`/api/sessions/${sessionId}`)
            .then((r) => (r.ok ? r.json() : null))
            .then((snapshot) => {
              if (snapshot && store.currentSessionId === sessionId) store.setSnapshot(snapshot);
            })
            .catch(() => {});
        }
      } catch (err) {
        console.error('Failed parsing SSE payload:', err);
      }
    };

    es.onerror = (err) => {
      if (globalEventSource !== es) return;
      console.warn('SSE disconnected or stream closed:', err);
      const wasRunning = store.isRunning;
      store.isConnected = false;
      store.setPhase('idle');
      closeSse();
      if (wasRunning) ui.reportBackendFailure();
    };
  }

  function closeSse() {
    if (globalEventSource) {
      try {
        globalEventSource.close();
      } catch (e) {
        // already closed
      }
      globalEventSource = null;
    }
    activeSessionId = null;
    store.isConnected = false;
  }

  return { connectSse, closeSse };
}
