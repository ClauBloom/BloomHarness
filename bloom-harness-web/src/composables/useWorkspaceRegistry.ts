import { computed, reactive } from 'vue';
import { basename } from '@/utils/format';

/**
 * Browser-local workspace registry (dsh keeps this on the Host; BloomHarness
 * has no workspace API, so the shell owns it). A workspace is a folder the
 * user opened at least once; it exists independently of sessions, so a freshly
 * added folder shows up in the sidebar immediately.
 *
 * "Archive session" is also browser-local: the backend has no delete/archive
 * endpoint, so archived ids are hidden from the tree and kept in localStorage.
 */
export interface LocalWorkspace {
  id: string;
  path: string;
  name: string;
  createdAt: number;
  updatedAt: number;
}

const WORKSPACE_KEY = 'bloom.workspaces.v1';
const HIDDEN_KEY = 'bloom.hidden-sessions.v1';
const ORDER_KEY = 'bloom.workspace-order.v1';

function read<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

const workspaces = reactive<LocalWorkspace[]>(read<LocalWorkspace[]>(WORKSPACE_KEY, []));
const hiddenSessions = reactive<string[]>(read<string[]>(HIDDEN_KEY, []));
const workspaceOrder = reactive<string[]>(read<string[]>(ORDER_KEY, []));

// Heal the order list: every workspace appears exactly once, unknown ids drop out.
(function reconcileOrder() {
  const known = new Set(workspaces.map((w) => w.id));
  const seen = new Set<string>();
  const next: string[] = [];
  for (const id of workspaceOrder) {
    if (known.has(id) && !seen.has(id)) {
      next.push(id);
      seen.add(id);
    }
  }
  for (const w of workspaces) if (!seen.has(w.id)) next.push(w.id);
  workspaceOrder.splice(0, workspaceOrder.length, ...next);
})();

function persist() {
  try {
    localStorage.setItem(WORKSPACE_KEY, JSON.stringify(workspaces));
    localStorage.setItem(HIDDEN_KEY, JSON.stringify(hiddenSessions));
    localStorage.setItem(ORDER_KEY, JSON.stringify(workspaceOrder));
  } catch {
    // localStorage is optional; the active app remains usable without it.
  }
}

/** Case-insensitive, separator-agnostic path key (Windows paths are case-insensitive). */
export function workspaceKey(path: string | undefined | null): string {
  return (path ?? '').replace(/\//g, '\\').replace(/[\\]+$/, '').toLowerCase();
}

/** Workspaces in manual (user) order. */
const orderedWorkspaces = computed<LocalWorkspace[]>(() => {
  const byId = new Map(workspaces.map((w) => [w.id, w]));
  const out: LocalWorkspace[] = [];
  for (const id of workspaceOrder) {
    const w = byId.get(id);
    if (w) out.push(w);
  }
  for (const w of workspaces) if (!workspaceOrder.includes(w.id)) out.push(w);
  return out;
});

function workspaceForPath(path?: string | null): LocalWorkspace | undefined {
  const key = workspaceKey(path);
  if (!key) return undefined;
  return workspaces.find((w) => workspaceKey(w.path) === key);
}

/** Register (or touch) a workspace; returns the registry row. Appends at the end in manual order. */
function addWorkspace(path: string, name?: string): LocalWorkspace | null {
  const trimmed = path.trim();
  if (!trimmed) return null;
  const existing = workspaceForPath(trimmed);
  if (existing) {
    existing.updatedAt = Date.now();
    if (name?.trim() && existing.name === basename(existing.path)) existing.name = name.trim();
    persist();
    return existing;
  }
  const now = Date.now();
  const workspace: LocalWorkspace = {
    id: `workspace-${now}-${Math.random().toString(36).slice(2, 8)}`,
    path: trimmed,
    name: name?.trim() || basename(trimmed),
    createdAt: now,
    updatedAt: now,
  };
  workspaces.push(workspace);
  workspaceOrder.push(workspace.id);
  persist();
  return workspace;
}

function renameWorkspace(id: string, name: string) {
  const workspace = workspaces.find((item) => item.id === id);
  const next = name.trim();
  if (!workspace || !next) return;
  workspace.name = next;
  workspace.updatedAt = Date.now();
  persist();
}

/** True when another workspace already carries `name` (case-insensitive). */
function workspaceNameTaken(name: string, exceptId?: string): boolean {
  const n = name.trim().toLowerCase();
  return workspaces.some((w) => w.id !== exceptId && w.name.trim().toLowerCase() === n);
}

/** Remove a workspace from the list only; folders and sessions are untouched (dsh semantics). */
function removeWorkspace(id: string) {
  const index = workspaces.findIndex((item) => item.id === id);
  if (index >= 0) workspaces.splice(index, 1);
  const orderIndex = workspaceOrder.indexOf(id);
  if (orderIndex >= 0) workspaceOrder.splice(orderIndex, 1);
  persist();
}

/** Manual reorder: move `id` before `beforeId` (`null` = to the end). */
function moveWorkspaceBefore(id: string, beforeId: string | null) {
  if (id === beforeId) return;
  const current = workspaceOrder.indexOf(id);
  if (current < 0) return;
  workspaceOrder.splice(current, 1);
  const target = beforeId ? workspaceOrder.indexOf(beforeId) : -1;
  if (target < 0) workspaceOrder.push(id);
  else workspaceOrder.splice(target, 0, id);
  persist();
}

function isHidden(sessionId: string) {
  return hiddenSessions.includes(sessionId);
}

function setHidden(sessionId: string, hidden: boolean) {
  const index = hiddenSessions.indexOf(sessionId);
  if (hidden && index < 0) hiddenSessions.push(sessionId);
  if (!hidden && index >= 0) hiddenSessions.splice(index, 1);
  persist();
}

export function useWorkspaceRegistry() {
  return {
    workspaces,
    orderedWorkspaces,
    hiddenSessions,
    workspaceOrder,
    workspaceKey,
    addWorkspace,
    renameWorkspace,
    workspaceNameTaken,
    removeWorkspace,
    moveWorkspaceBefore,
    isHidden,
    setHidden,
    workspaceForPath,
  };
}
