import { reactive, watch } from 'vue';
import type { SessionMetadata } from '@/types/session.types';

/**
 * Sidebar view state (port of dsh ui-workspace `stores.ts` + the ordering
 * policy in `WorkspaceBrowser.tsx`):
 *
 * - `groupBy`   'workspace' (tree) | 'flat' (one list)
 * - `orderBy`   'manual' (stored order, new sessions appended, never re-sorted)
 *               | 'updated' (full recency sort once on switch, then only sessions
 *               whose `updatedAt` moved are promoted to the top)
 * - session order is kept per *account*: one per workspace key, `''` for
 *   ungrouped sessions and `__flat__` for the flat list.
 * - `showArchived` is a BloomHarness addition (archive is browser-local here).
 */
export type SessionGroupBy = 'workspace' | 'flat';
export type SessionOrderBy = 'manual' | 'updated';

export const UNGROUPED_ACCOUNT = '';
export const FLAT_ACCOUNT = '__flat__';

interface ViewState {
  groupBy: SessionGroupBy;
  orderBy: SessionOrderBy;
  groupExpansion: Record<string, boolean>;
  sessionOrderByAccount: Record<string, string[]>;
  sessionUpdatedAtByAccount: Record<string, Record<string, number>>;
  showArchived: boolean;
}

const STORAGE_KEY = 'bloom.workspace.view.v2';
const LEGACY_KEY = 'bloom.workspace.view.v1';

function load(): ViewState {
  const base: ViewState = {
    groupBy: 'workspace',
    orderBy: 'updated',
    groupExpansion: {},
    sessionOrderByAccount: {},
    sessionUpdatedAtByAccount: {},
    showArchived: false,
  };
  try {
    const raw = localStorage.getItem(STORAGE_KEY) ?? localStorage.getItem(LEGACY_KEY);
    if (!raw) return base;
    const parsed = JSON.parse(raw) as Partial<ViewState> & { orderBy?: string };
    return {
      groupBy: parsed.groupBy === 'flat' ? 'flat' : 'workspace',
      orderBy: parsed.orderBy === 'manual' ? 'manual' : 'updated',
      groupExpansion: isRecord(parsed.groupExpansion) ? (parsed.groupExpansion as Record<string, boolean>) : {},
      sessionOrderByAccount: isRecord(parsed.sessionOrderByAccount)
        ? (parsed.sessionOrderByAccount as Record<string, string[]>)
        : {},
      sessionUpdatedAtByAccount: isRecord(parsed.sessionUpdatedAtByAccount)
        ? (parsed.sessionUpdatedAtByAccount as Record<string, Record<string, number>>)
        : {},
      showArchived: parsed.showArchived === true,
    };
  } catch {
    return base;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

const state = reactive<ViewState>(load());

watch(
  state,
  () => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {
      // storage unavailable: view prefs stay session-local
    }
  },
  { deep: true },
);

// ---- ordering policy (dsh WorkspaceBrowser.tsx) ----

/** Stored order first (dropping unknown/duplicate ids), then unseen ids in account order. */
export function reconciledSessionOrder(sessionIds: readonly string[], previous: readonly string[] | undefined): string[] {
  const known = new Set(sessionIds);
  const seen = new Set<string>();
  const order: string[] = [];
  for (const id of previous ?? []) {
    if (known.has(id) && !seen.has(id)) {
      order.push(id);
      seen.add(id);
    }
  }
  for (const id of sessionIds) {
    if (!seen.has(id)) {
      order.push(id);
      seen.add(id);
    }
  }
  return order;
}

function activity(s: SessionMetadata | undefined): number {
  return s ? (s.updatedAt ?? s.createdAt) : 0;
}

/** `updatedAt` desc, id asc tie-break. */
export function compareSessionRecency(a: string, b: string, byId: Record<string, SessionMetadata>): number {
  const diff = activity(byId[b]) - activity(byId[a]);
  return diff !== 0 ? diff : a < b ? -1 : a > b ? 1 : 0;
}

interface NextOrderInput {
  sessionIds: readonly string[];
  previousOrder: readonly string[] | undefined;
  previousUpdatedAt: Record<string, number>;
  orderBy: SessionOrderBy;
  /** Full recency sort: first use of the account, or the switch manual → updated. */
  sortByRecency: boolean;
  byId: Record<string, SessionMetadata>;
}

export function nextSessionOrderAccount(input: NextOrderInput): { order: string[]; updatedAt: Record<string, number> } {
  const { sessionIds, previousOrder, previousUpdatedAt, orderBy, sortByRecency, byId } = input;
  let order = reconciledSessionOrder(sessionIds, previousOrder);
  if (sortByRecency) {
    order.sort((a, b) => compareSessionRecency(a, b, byId));
  } else if (orderBy === 'updated') {
    const promoted = sessionIds
      .filter((id) => {
        const s = byId[id];
        if (!s) return false;
        const seen = previousUpdatedAt[id];
        return seen === undefined || activity(s) > seen;
      })
      .sort((a, b) => compareSessionRecency(a, b, byId));
    if (promoted.length > 0) {
      const set = new Set(promoted);
      order = [...promoted, ...order.filter((id) => !set.has(id))];
    }
  }
  const updatedAt: Record<string, number> = {};
  for (const id of sessionIds) updatedAt[id] = activity(byId[id]);
  return { order, updatedAt };
}

function sameList(a: readonly string[] | undefined, b: readonly string[]): boolean {
  if (!a || a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
  return true;
}

// ---- actions ----

function setGroupBy(groupBy: SessionGroupBy) {
  state.groupBy = groupBy;
}

function setOrderBy(orderBy: SessionOrderBy) {
  state.orderBy = orderBy;
}

function setShowArchived(show: boolean) {
  state.showArchived = show;
}

function isGroupExpanded(key: string): boolean {
  return state.groupExpansion[key] ?? true;
}

function setGroupExpanded(key: string, expanded: boolean) {
  state.groupExpansion = { ...state.groupExpansion, [key]: expanded };
}

/** Reconcile one account against the current session set (dsh `syncSessionOrderAccount`). */
function syncAccount(account: string, sessionIds: readonly string[], byId: Record<string, SessionMetadata>, switchedToUpdated: boolean) {
  const previousOrder = state.sessionOrderByAccount[account];
  const previousUpdatedAt = state.sessionUpdatedAtByAccount[account] ?? {};
  const { order, updatedAt } = nextSessionOrderAccount({
    sessionIds,
    previousOrder,
    previousUpdatedAt,
    orderBy: state.orderBy,
    sortByRecency: state.orderBy === 'updated' && (previousOrder === undefined || switchedToUpdated),
    byId,
  });
  if (!sameList(previousOrder, order)) state.sessionOrderByAccount = { ...state.sessionOrderByAccount, [account]: order };
  const prevKeys = Object.keys(previousUpdatedAt);
  const changed =
    prevKeys.length !== Object.keys(updatedAt).length || Object.entries(updatedAt).some(([k, v]) => previousUpdatedAt[k] !== v);
  if (changed) state.sessionUpdatedAtByAccount = { ...state.sessionUpdatedAtByAccount, [account]: updatedAt };
}

/** Drop accounts that no longer exist (dsh `retainAccountKeys`). */
function retainAccounts(keys: readonly string[]) {
  const keep = new Set(keys);
  const nextOrder: Record<string, string[]> = {};
  const nextUpdated: Record<string, Record<string, number>> = {};
  for (const [k, v] of Object.entries(state.sessionOrderByAccount)) if (keep.has(k)) nextOrder[k] = v;
  for (const [k, v] of Object.entries(state.sessionUpdatedAtByAccount)) if (keep.has(k)) nextUpdated[k] = v;
  if (Object.keys(nextOrder).length !== Object.keys(state.sessionOrderByAccount).length) state.sessionOrderByAccount = nextOrder;
  if (Object.keys(nextUpdated).length !== Object.keys(state.sessionUpdatedAtByAccount).length) {
    state.sessionUpdatedAtByAccount = nextUpdated;
  }
}

function setSessionOrder(account: string, order: string[]) {
  state.sessionOrderByAccount = { ...state.sessionOrderByAccount, [account]: order };
}

/** Manual drag: move `id` before `beforeId` (`null` = end) inside one account. */
function moveSessionBefore(account: string, id: string, beforeId: string | null) {
  const current = [...(state.sessionOrderByAccount[account] ?? [])];
  const from = current.indexOf(id);
  if (from < 0 || id === beforeId) return;
  current.splice(from, 1);
  const to = beforeId ? current.indexOf(beforeId) : -1;
  if (to < 0) current.push(id);
  else current.splice(to, 0, id);
  setSessionOrder(account, current);
}

export function useWorkspaceView() {
  return {
    state,
    setGroupBy,
    setOrderBy,
    setShowArchived,
    isGroupExpanded,
    setGroupExpanded,
    syncAccount,
    retainAccounts,
    setSessionOrder,
    moveSessionBefore,
  };
}
