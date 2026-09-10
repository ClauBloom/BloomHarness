import { useAgentStore } from '@/stores/agentStore';
import { useUiStore } from '@/stores/uiStore';
import { useSse } from './useSse';
import { useWorkspace } from './useWorkspace';
import { useWorkspaceRegistry } from './useWorkspaceRegistry';
import { rememberSessionTitle, titleFromText } from './useSessionTitles';
import type { ModelRef, ThinkingLevel } from '@/types/message.types';
import type { SessionMetadata, SessionSnapshot } from '@/types/session.types';

export const SELECTED_MODEL_KEY = 'bloom_selected_model';

/** Derive a session title from the first prompt (first line, ≤40 chars). */
export function deriveSessionName(text: string): string {
  return titleFromText(text) || `新会话 ${new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
}

export function readSelectedModel(): ModelRef | null {
  try {
    const saved = localStorage.getItem(SELECTED_MODEL_KEY);
    if (saved && saved.includes(':')) {
      const [provider, ...rest] = saved.split(':');
      const id = rest.join(':');
      if (provider && id) return { provider, id };
    }
  } catch {
    // ignore
  }
  return null;
}

export function writeSelectedModel(model: ModelRef | null) {
  try {
    if (model) localStorage.setItem(SELECTED_MODEL_KEY, `${model.provider}:${model.id}`);
    else localStorage.removeItem(SELECTED_MODEL_KEY);
  } catch {
    // ignore
  }
}

export function useSession() {
  const store = useAgentStore();
  const ui = useUiStore();
  const { connectSse } = useSse();
  const { loadWorkspaceInfo } = useWorkspace();
  const { addWorkspace } = useWorkspaceRegistry();

  async function fetchSessions(): Promise<SessionMetadata[]> {
    try {
      const res = await fetch('/api/sessions');
      if (res.ok) {
        const list = (await res.json()) as SessionMetadata[];
        for (const session of list) {
          if (session.cwd) addWorkspace(session.cwd);
        }
        store.setSessionList(list);
        ui.reportBackendOk();
        return list;
      }
      console.warn('Failed to fetch sessions, status:', res.status);
      ui.reportBackendFailure();
    } catch (e) {
      console.error('Failed to fetch sessions:', e);
      ui.reportBackendFailure();
      store.setError(`获取会话列表失败: ${e instanceof Error ? e.message : e}（请确认后端服务已启动）`);
    }
    return [];
  }

  async function updateSessionModel(sessionId: string, model: ModelRef) {
    try {
      const res = await fetch(`/api/sessions/${sessionId}/model`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(model),
      });
      if (res.ok && store.currentSession && store.currentSession.id === sessionId) {
        store.currentSession.model = model;
      }
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

  async function createSession(
    name?: string,
    cwd?: string,
    model?: ModelRef | null,
    thinkingLevel?: ThinkingLevel,
  ): Promise<string | null> {
    store.isCreatingSession = true;
    try {
      const defaultName = name || `新会话 ${new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
      const activeModel = model === undefined ? readSelectedModel() : model;
      const payload: Record<string, unknown> = { name: defaultName, cwd: cwd || '' };
      if (activeModel) payload.model = activeModel;
      if (thinkingLevel) payload.thinkingLevel = thinkingLevel;

      const res = await fetch('/api/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (res.ok) {
        const data = await res.json();
        const newId = data.id as string;
        // The backend does not persist `name`; keep the title client-side.
        rememberSessionTitle(newId, defaultName);
        await selectSession(newId);
        await fetchSessions();
        return newId;
      }
      const errText = await res.text();
      throw new Error(errText || `HTTP ${res.status}`);
    } catch (e: any) {
      console.error('Failed creating session:', e);
      store.setError(`创建会话失败: ${e.message || e}`);
      return null;
    } finally {
      store.isCreatingSession = false;
    }
  }

  async function selectSession(sessionId: string): Promise<SessionSnapshot | undefined> {
    try {
      const res = await fetch(`/api/sessions/${sessionId}`);
      if (res.ok) {
        const snapshot = (await res.json()) as SessionSnapshot;
        store.setSnapshot(snapshot);
        ui.reportBackendOk();
        connectSse(sessionId);
        return snapshot;
      }
      throw new Error(`HTTP ${res.status}`);
    } catch (e: any) {
      console.error('Failed selecting session:', e);
      store.setError(`切换会话失败: ${e.message || e}`);
    }
    return undefined;
  }

  /**
   * Show the blank hero. `cwd` defaults to the current session's workspace,
   * then to the backend's default workspace.
   */
  async function startBlankSession(cwd?: string | null) {
    let target = cwd ?? store.currentSession?.cwd ?? store.blankSessionCwd ?? null;
    if (!target) {
      const info = await loadWorkspaceInfo();
      target = info?.defaultWorkspace ?? null;
    }
    store.startBlank(target);
  }

  async function sendPrompt(sessionId: string | null | undefined, text: string) {
    const trimmed = text.trim();
    if (!trimmed) return;

    const activeModel = readSelectedModel();
    if (!activeModel) {
      store.setError('尚未配置或选择 AI 模型，请点击设置(⚙️)配置服务商与模型');
      return;
    }

    let targetId = sessionId;

    if (!targetId) {
      // 空白会话：首次发送时才真正创建，名称取自首条提示
      targetId = await createSession(deriveSessionName(trimmed), store.blankSessionCwd ?? '', activeModel);
      if (!targetId) {
        store.setError('未能创建会话，指令发送终止');
        return;
      }
    } else {
      const current = store.currentSession?.model;
      if (!current || current.provider !== activeModel.provider || current.id !== activeModel.id) {
        await updateSessionModel(targetId, activeModel);
      }
    }

    store.isSending = true;
    store.setPhase('turn');

    try {
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
      let msg = e.message || String(e);
      if (/Agent loop execution error/i.test(msg) || /尚未配置或选择/i.test(msg)) {
        msg = '尚未配置或选择 AI 模型，请点击设置(⚙️)配置服务商与模型。';
      } else {
        msg = `发送指令失败: ${msg}`;
      }
      store.setError(msg);
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
      const res = await fetch(`/api/input/${targetId}/abort`, { method: 'POST' });
      if (res.ok) {
        await selectSession(targetId);
      }
    } catch (e: any) {
      console.error('Failed aborting session:', e);
      store.setError(`中断会话失败: ${e.message || e}`);
    }
  }

  async function steerSession(sessionId?: string | null, text?: string): Promise<boolean> {
    const targetId = sessionId || store.currentSessionId;
    if (!targetId || !text || !text.trim()) return false;
    const trimmed = text.trim();
    try {
      const res = await fetch(`/api/input/${targetId}/steer`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text: trimmed }),
      });
      if (res.ok) {
        ui.pushToast('已发送引导指令，将在当前步骤后生效', 'info');
        return true;
      }
      const err = await res.text();
      throw new Error(err || `HTTP ${res.status}`);
    } catch (e: any) {
      console.error('Failed steering session:', e);
      store.setError(`引导失败: ${e?.message || e}`);
      return false;
    }
  }

  return {
    fetchSessions,
    createSession,
    selectSession,
    startBlankSession,
    sendPrompt,
    abortSession,
    steerSession,
    updateSessionCwd,
    updateSessionModel,
  };
}
