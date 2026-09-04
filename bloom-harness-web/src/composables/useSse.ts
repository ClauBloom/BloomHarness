import { useAgentStore } from '@/stores/agentStore';

export function useSse() {
  const store = useAgentStore();
  let eventSource: EventSource | null = null;
  let activeSessionId: string | null = null;

  function connectSse(sessionId: string) {
    if (eventSource && activeSessionId === sessionId) {
      // Already connected to this session's SSE stream
      return;
    }

    if (eventSource) {
      eventSource.close();
      eventSource = null;
    }

    activeSessionId = sessionId;
    eventSource = new EventSource(`/api/stream/${sessionId}`);

    eventSource.onopen = () => {
      store.isConnected = true;
    };

    eventSource.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        if (data.type === 'chunk' && data.payload) {
          const payload = data.payload;
          if (payload.type === 'assistant_delta') {
            store.appendDelta(payload.kind, payload.delta);
          } else if (payload.type === 'item_started' || payload.type === 'item_updated' || payload.type === 'item_finished') {
            store.updateItem(payload.item);
          }
        } else if (data.type === 'error') {
          console.error('[SSE Error Event]', data);
          store.setError(data.message || '模型执行过程出现异常');
          store.flushStreaming();
          store.setPhase('idle');
        } else if (data.type === 'end') {
          store.flushStreaming();
          store.setPhase('idle');
          // Fetch updated snapshot to sync state
          fetch(`/api/sessions/${sessionId}`)
            .then(r => r.ok ? r.json() : null)
            .then(snapshot => {
              if (snapshot) store.setSnapshot(snapshot);
            })
            .catch(() => {});
        }
      } catch (err) {
        console.error('Failed parsing SSE payload:', err);
      }
    };

    eventSource.onerror = (err) => {
      console.warn('SSE disconnected or stream closed:', err);
      store.isConnected = false;
      store.setPhase('idle');
      eventSource?.close();
      eventSource = null;
      activeSessionId = null;
    };
  }

  function closeSse() {
    if (eventSource) {
      eventSource.close();
      eventSource = null;
      activeSessionId = null;
      store.isConnected = false;
    }
  }

  return { connectSse, closeSse };
}
