import { ref } from 'vue';
import type { DirectoryListing, WorkspaceInfo } from '@/types/session.types';
import { useUiStore } from '@/stores/uiStore';
import { useWorkspaceRegistry } from './useWorkspaceRegistry';

// 模块级单例：工作区元信息只需拉取一次
const workspaceInfo = ref<WorkspaceInfo | null>(null);
let infoPromise: Promise<WorkspaceInfo | null> | null = null;

export interface ValidateResult {
  valid: boolean;
  canonicalPath?: string;
  name?: string;
  isWritable?: boolean;
  error?: string;
}

/** Workspace REST wrappers (`/api/workspace/*`). */
export function useWorkspace() {
  const ui = useUiStore();
  const registry = useWorkspaceRegistry();

  async function loadWorkspaceInfo(): Promise<WorkspaceInfo | null> {
    if (workspaceInfo.value) return workspaceInfo.value;
    if (!infoPromise) {
      infoPromise = fetch('/api/workspace/current')
        .then(async (r) => {
          if (!r.ok) throw new Error(`HTTP ${r.status}`);
          const info = (await r.json()) as WorkspaceInfo;
          workspaceInfo.value = info;
          ui.reportBackendOk();
          return info;
        })
        .catch((e) => {
          console.warn('Failed loading workspace info:', e);
          infoPromise = null;
          return null;
        });
    }
    return infoPromise;
  }

  async function fetchRecentWorkspaces(): Promise<string[]> {
    try {
      const r = await fetch('/api/workspace/recent');
      if (!r.ok) return [];
      const list = await r.json();
      return Array.isArray(list) ? list.filter((p): p is string => typeof p === 'string' && p.length > 0) : [];
    } catch {
      return [];
    }
  }

  async function browse(path?: string, signal?: AbortSignal): Promise<DirectoryListing> {
    const query = path ? `?path=${encodeURIComponent(path)}` : '';
    const r = await fetch(`/api/workspace/browse${query}`, { signal });
    const data = await r.json();
    if (!r.ok || data.error) throw new Error(data.error || `HTTP ${r.status}`);
    return data as DirectoryListing;
  }

  async function validate(path: string): Promise<ValidateResult> {
    const r = await fetch('/api/workspace/validate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ path }),
    });
    const data = await r.json();
    return data as ValidateResult;
  }

  /** Opens the OS folder picker on the backend host; `null` when cancelled. */
  async function pickSystemFolder(initialPath?: string): Promise<string | null> {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 120_000);
    let r: Response;
    try {
      r = await fetch('/api/workspace/pick-folder', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ initialPath: initialPath || '' }),
        signal: controller.signal,
      });
    } catch (error) {
      if (controller.signal.aborted) throw new Error('系统文件夹选择器等待超时，请使用路径输入。');
      throw error;
    } finally {
      window.clearTimeout(timeout);
    }
    if (!r.ok) throw new Error(`系统文件夹选择器不可用（HTTP ${r.status}）`);
    const data = await r.json();
    if (data.success && typeof data.path === 'string') return data.path;
    if (typeof data.message === 'string' && !data.message.includes('已关闭')) {
      throw new Error(data.message);
    }
    return null;
  }

  return {
    workspaceInfo,
    loadWorkspaceInfo,
    fetchRecentWorkspaces,
    browse,
    validate,
    pickSystemFolder,
    registry,
  };
}
