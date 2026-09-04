import { useAgentStore } from '@/stores/agentStore';
import { useSse } from './useSse';

export function useSession() {
  const store = useAgentStore();
  const { connectSse } = useSse();

  async function fetchSessions() {
    try {
      const res = await fetch('/api/sessions');
      if (res.ok) {
        const list = await res.json();
        store.setSessionList(list);
        return list;
      } else {
        console.warn('Failed to fetch sessions, status:', res.status);
      }
    } catch (e: any) {
      console.error('Failed to fetch sessions:', e);
      store.setError('获取会话列表失败，请确认后端服务已启动');
    }
    return [];
  }

  function getActiveModelRef(): { provider: string; id: string } | null {
    try {
      const saved = localStorage.getItem('bloom_selected_model');
      if (saved && saved.includes(':')) {
        const [provider, ...rest] = saved.split(':');
        const id = rest.join(':');
        if (provider && id) {
          return { provider, id };
        }
      }
    } catch (e) {
      // ignore
    }
    return null;
  }

  async function updateSessionModel(sessionId: string, model: { provider: string; id: string }) {
    try {
      await fetch(`/api/sessions/${sessionId}/model`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(model),
      });
    } catch (e) {
      console.warn('Failed updating session model:', e);
    }
  }

  async function updateSessionCwd(sessionId: string, newCwd: string) {
    try {
      const res = await fetch(`/api/sessions/${sessionId}/cwd`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ cwd: newCwd }),
      });
      if (res.ok) {
        if (store.currentSession && store.currentSession.id === sessionId) {
          store.currentSession.cwd = newCwd;
        }
        await fetchSessions();
        return true;
      }
    } catch (e) {
      console.warn('Failed updating session cwd:', e);
    }
    return false;
  }

  async function createSession(name?: string, cwd?: string, model?: { provider: string; id: string }): Promise<string | null> {
    store.isCreatingSession = true;
    try {
      const defaultName = name || `新会话 ${new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
      const activeModel = model || getActiveModelRef();
      const payload: Record<string, any> = { name: defaultName, cwd: cwd || '' };
      if (activeModel) {
        payload.model = activeModel;
      }

      const res = await fetch('/api/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (res.ok) {
        const data = await res.json();
        const newId = data.id;
        await selectSession(newId);
        await fetchSessions();
        return newId;
      } else {
        const errText = await res.text();
        throw new Error(errText || `HTTP ${res.status}`);
      }
    } catch (e: any) {
      console.error('Failed creating session:', e);
      store.setError(`创建会话失败: ${e.message || e}`);
      return null;
    } finally {
      store.isCreatingSession = false;
    }
  }

  async function selectSession(sessionId: string) {
    try {
      const res = await fetch(`/api/sessions/${sessionId}`);
      if (res.ok) {
        const snapshot = await res.json();
        store.setSnapshot(snapshot);
        // Connect SSE stream for the selected session
        connectSse(sessionId);
        return snapshot;
      } else {
        throw new Error(`HTTP ${res.status}`);
      }
    } catch (e: any) {
      console.error('Failed selecting session:', e);
      store.setError(`切换会话失败: ${e.message || e}`);
    }
  }

  async function sendPrompt(sessionId: string | null | undefined, text: string) {
    const trimmed = text.trim();
    if (!trimmed) return;

    let targetId = sessionId;

    // 关键兜底：如果当前没有会话，自动无缝新建一个会话！
    if (!targetId) {
      console.log('No active session, creating a new one automatically...');
      targetId = await createSession();
      if (!targetId) {
        store.setError('未能自动创建会话，指令发送终止');
        return;
      }
    } else {
      // 保证当前会话与选中的模型同步
      const activeModel = getActiveModelRef();
      if (activeModel) {
        await updateSessionModel(targetId, activeModel);
      }
    }

    store.isSending = true;
    store.setPhase('turn');

    try {
      // 确保建立 SSE 监听
      connectSse(targetId);

      const res = await fetch(`/api/input/${targetId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text: trimmed }),
      });

      if (!res.ok) {
        const errorText = await res.text();
        throw new Error(errorText || `HTTP ${res.status}`);
      }
    } catch (e: any) {
      console.error('Failed sending prompt:', e);
      store.setError(`发送指令失败: ${e.message || e}`);
      store.setPhase('idle');
    } finally {
      store.isSending = false;
    }
  }

  async function abortSession(sessionId?: string | null) {
    const targetId = sessionId || store.currentSessionId;
    if (!targetId) return;

    try {
      store.setPhase('idle');
      store.flushStreaming();
      const res = await fetch(`/api/input/${targetId}/abort`, {
        method: 'POST',
      });
      if (res.ok) {
        // Refresh session snapshot to get latest transcript
        await selectSession(targetId);
      }
    } catch (e: any) {
      console.error('Failed aborting session:', e);
      store.setError(`中断会话失败: ${e.message || e}`);
    }
  }

  return {
    fetchSessions,
    createSession,
    selectSession,
    sendPrompt,
    abortSession,
    updateSessionCwd,
  };
}
