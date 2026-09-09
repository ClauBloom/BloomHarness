<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useAgentStore } from '@/stores/agentStore';
import { useUiStore } from '@/stores/uiStore';
import { useSession } from '@/composables/useSession';
import { useSessionTitles } from '@/composables/useSessionTitles';
import { useWorkspace } from '@/composables/useWorkspace';
import {
  LocalWorkspace,
  useWorkspaceRegistry,
  workspaceKey,
} from '@/composables/useWorkspaceRegistry';
import {
  FLAT_ACCOUNT,
  SessionGroupBy,
  SessionOrderBy,
  UNGROUPED_ACCOUNT,
  useWorkspaceView,
} from '@/composables/useWorkspaceView';
import type { SessionMetadata } from '@/types/session.types';
import DsButton from '@/components/primitives/DsButton.vue';
import DsMenu from '@/components/primitives/DsMenu.vue';
import DsModal from '@/components/primitives/DsModal.vue';
import DsTooltip from '@/components/primitives/DsTooltip.vue';
import type { MenuEntry } from '@/components/primitives/menu.types';
import {
  IconClose,
  IconProjectAdd,
  IconSearch,
  IconViewOptions,
  ICON_STROKE,
} from '@/components/primitives/icons';
import { basename } from '@/utils/format';
import ProjectRow from './ProjectRow.vue';
import SessionRow from './SessionRow.vue';

/**
 * dsh WorkspaceBrowser: section header (label, inline search, view options, add
 * workspace) + the session tree grouped by workspace or flat.
 *
 * View semantics match dsh:
 * - Group order ALWAYS follows the workspace registry (reordered by drag-drop);
 *   groups are NEVER resorted by recency.
 * - `orderBy === 'manual'`: stored session order, new sessions appended.
 * - `orderBy === 'updated'`: full recency sort once on switch, then only
 *   sessions whose `updatedAt` moved are promoted to the top.
 * - Blank session ("新会话") is pinned to the top of its workspace group.
 * - Modals for rename workspace, delete workspace, rename session.
 * - Archive/unarchive actions (local hiding); optional view-menu toggle.
 */
defineProps<{ wide: boolean }>();
const emit = defineEmits<{ expandSidebar: [] }>();

const store = useAgentStore();
const ui = useUiStore();
const { selectSession, startBlankSession } = useSession();
const { sessionTitle, rememberSessionTitle } = useSessionTitles();
const { fetchRecentWorkspaces } = useWorkspace();
const registry = useWorkspaceRegistry();
const view = useWorkspaceView();

const COLLAPSED_SESSION_LIMIT = 5;

// ---- relative-time ticker ----
const now = ref(Date.now());
let ticker: ReturnType<typeof setInterval> | null = null;
onMounted(async () => {
  ticker = setInterval(() => (now.value = Date.now()), 60_000);
  for (const path of await fetchRecentWorkspaces()) registry.addWorkspace(path);
});
onBeforeUnmount(() => {
  if (ticker) clearInterval(ticker);
});

// ---- session lookup by id ----
const sessionsById = computed<Record<string, SessionMetadata>>(() => {
  const map: Record<string, SessionMetadata> = {};
  for (const s of store.sessionList) map[s.id] = s;
  return map;
});

// Track switches to "updated" so nextSessionOrderAccount can do a full initial sort.
const previousOrderBy = ref<SessionOrderBy>(view.state.orderBy);

// Keep per-account session order in sync with sessionList and view options.
watch(
  [() => store.sessionList, () => view.state.orderBy, () => registry.hiddenSessions.length],
  () => {
    const switchedToUpdated = previousOrderBy.value === 'manual' && view.state.orderBy === 'updated';
    previousOrderBy.value = view.state.orderBy;

    const byId = sessionsById.value;
    const accountKeys = new Set<string>();

    // 1. Every known workspace account
    for (const w of registry.workspaces) {
      const key = workspaceKey(w.path);
      accountKeys.add(key);
      const ids = store.sessionList.filter((s) => workspaceKey(s.cwd) === key).map((s) => s.id);
      view.syncAccount(key, ids, byId, switchedToUpdated);
    }

    // 2. Ungrouped sessions (sessions whose cwd is not in the registry)
    const knownKeys = new Set(registry.workspaces.map((w) => workspaceKey(w.path)));
    const ungroupedIds = store.sessionList.filter((s) => !knownKeys.has(workspaceKey(s.cwd))).map((s) => s.id);
    accountKeys.add(UNGROUPED_ACCOUNT);
    view.syncAccount(UNGROUPED_ACCOUNT, ungroupedIds, byId, switchedToUpdated);

    // 3. Flat account
    accountKeys.add(FLAT_ACCOUNT);
    view.syncAccount(FLAT_ACCOUNT, store.sessionList.map((s) => s.id), byId, switchedToUpdated);

    view.retainAccounts([...accountKeys]);
  },
  { immediate: true, deep: true },
);

// Auto-expand the group containing the current session.
watch(
  () => store.currentSessionId,
  (id) => {
    if (!id) return;
    const s = store.sessionList.find((x) => x.id === id);
    if (!s) return;
    const key = workspaceKey(s.cwd);
    if (!view.isGroupExpanded(key)) view.setGroupExpanded(key, true);
  },
);

// Auto-expand the group containing the hero blank workspace.
watch(
  () => store.blankSessionCwd,
  (cwd) => {
    if (!cwd) return;
    const key = workspaceKey(cwd);
    if (!view.isGroupExpanded(key)) view.setGroupExpanded(key, true);
  },
);

// ---- groups (dsh: always ordered by registry; ungrouped at the bottom) ----
interface GroupModel {
  key: string;
  workspaceId?: string;
  label: string;
  path: string;
  sessions: SessionMetadata[];
  isCurrentBlankHere: boolean;
  containsCurrent: boolean;
  ungrouped?: boolean;
}

const groups = computed<GroupModel[]>(() => {
  const currentId = store.currentSessionId;
  const blankCwdKey = store.blankSessionCwd ? workspaceKey(store.blankSessionCwd) : null;
  const isBlank = !currentId;
  const showArchived = view.state.showArchived;
  const hidden = new Set(registry.hiddenSessions);
  const byId = sessionsById.value;

  const result: GroupModel[] = [];
  const registeredKeys = new Set<string>();

  for (const w of registry.orderedWorkspaces.value) {
    const key = workspaceKey(w.path);
    registeredKeys.add(key);

    const orderedIds = view.state.sessionOrderByAccount[key] ?? [];
    const sessions: SessionMetadata[] = [];
    for (const id of orderedIds) {
      const s = byId[id];
      if (!s) continue;
      if (!showArchived && hidden.has(s.id)) continue;
      sessions.push(s);
    }

    const isCurrentBlankHere = isBlank && blankCwdKey === key;
    const containsCurrent = isCurrentBlankHere || sessions.some((s) => s.id === currentId);

    result.push({
      key,
      workspaceId: w.id,
      label: w.name,
      path: w.path,
      sessions,
      isCurrentBlankHere,
      containsCurrent,
    });
  }

  // Ungrouped bucket: only present when there are sessions outside registered workspaces
  const ungroupedIds = view.state.sessionOrderByAccount[UNGROUPED_ACCOUNT] ?? [];
  const ungroupedSessions: SessionMetadata[] = [];
  for (const id of ungroupedIds) {
    const s = byId[id];
    if (!s) continue;
    if (registeredKeys.has(workspaceKey(s.cwd))) continue;
    if (!showArchived && hidden.has(s.id)) continue;
    ungroupedSessions.push(s);
  }
  const isBlankUngrouped = isBlank && (!blankCwdKey || !registeredKeys.has(blankCwdKey));
  if (ungroupedSessions.length > 0 || isBlankUngrouped) {
    result.push({
      key: UNGROUPED_ACCOUNT,
      label: '未分组',
      path: '',
      sessions: ungroupedSessions,
      isCurrentBlankHere: isBlankUngrouped,
      containsCurrent: isBlankUngrouped || ungroupedSessions.some((s) => s.id === currentId),
      ungrouped: true,
    });
  }

  return result;
});

// Flat session list (reconciled flat order, filtered by archived)
const flatSessions = computed<SessionMetadata[]>(() => {
  const showArchived = view.state.showArchived;
  const hidden = new Set(registry.hiddenSessions);
  const byId = sessionsById.value;
  const orderedIds = view.state.sessionOrderByAccount[FLAT_ACCOUNT] ?? [];
  const out: SessionMetadata[] = [];
  for (const id of orderedIds) {
    const s = byId[id];
    if (!s) continue;
    if (!showArchived && hidden.has(s.id)) continue;
    out.push(s);
  }
  return out;
});

// Per-group overflow (dsh limits to 5, button toggles)
const overflowOpen = ref<Record<string, boolean>>({});

function visibleSessions(g: GroupModel): SessionMetadata[] {
  if (overflowOpen.value[g.key] || g.sessions.length <= COLLAPSED_SESSION_LIMIT) return g.sessions;
  return g.sessions.slice(0, COLLAPSED_SESSION_LIMIT);
}
function hiddenCount(g: GroupModel): number {
  return overflowOpen.value[g.key] ? 0 : Math.max(0, g.sessions.length - COLLAPSED_SESSION_LIMIT);
}

function isRunning(s: SessionMetadata): boolean {
  return s.id === store.currentSessionId && store.isRunning;
}

// ---- drag and drop (native HTML5, dsh semantics) ----
// Workspaces reorder the registry; sessions reorder inside their account only.
interface DragState {
  kind: 'workspace' | 'session';
  id: string;
  account?: string;
  targetId: string | null;
  marker: 'before' | 'after' | null;
}
const drag = ref<DragState | null>(null);

function onWsDragStart(event: DragEvent, workspaceId: string) {
  drag.value = { kind: 'workspace', id: workspaceId, targetId: null, marker: null };
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', workspaceId);
  }
}

function onWsDragOver(event: DragEvent, workspaceId: string) {
  if (!drag.value || drag.value.kind !== 'workspace') return;
  event.preventDefault();
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move';
  const el = event.currentTarget as HTMLElement | null;
  if (!el) return;
  const r = el.getBoundingClientRect();
  const half = event.clientY < r.top + r.height / 2 ? 'before' : 'after';
  drag.value.targetId = workspaceId;
  drag.value.marker = half;
}

function onWsDrop(event: DragEvent, workspaceId: string) {
  if (!drag.value || drag.value.kind !== 'workspace') return;
  event.preventDefault();
  const sourceId = drag.value.id;
  const marker = drag.value.marker;
  commitWorkspaceDrop(sourceId, workspaceId, marker);
  drag.value = null;
}

function commitWorkspaceDrop(sourceId: string, targetId: string, marker: 'before' | 'after' | null) {
  if (sourceId === targetId) return;
  const list = [...registry.workspaceOrder];
  const targetIndex = list.indexOf(targetId);
  if (targetIndex < 0) return;
  const beforeId = marker === 'before' ? targetId : list[targetIndex + 1] ?? null;
  registry.moveWorkspaceBefore(sourceId, beforeId);
}

function onSessionDragStart(event: DragEvent, account: string, sessionId: string) {
  drag.value = { kind: 'session', id: sessionId, account, targetId: null, marker: null };
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', sessionId);
  }
}

function onSessionDragOver(event: DragEvent, account: string, sessionId: string) {
  if (!drag.value || drag.value.kind !== 'session' || drag.value.account !== account) return;
  event.preventDefault();
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move';
  const el = event.currentTarget as HTMLElement | null;
  if (!el) return;
  const r = el.getBoundingClientRect();
  const half = event.clientY < r.top + r.height / 2 ? 'before' : 'after';
  drag.value.targetId = sessionId;
  drag.value.marker = half;
}

function onSessionDrop(event: DragEvent, account: string, sessionId: string) {
  if (!drag.value || drag.value.kind !== 'session' || drag.value.account !== account) return;
  event.preventDefault();
  const sourceId = drag.value.id;
  const marker = drag.value.marker;
  commitSessionDrop(account, sourceId, sessionId, marker);
  drag.value = null;
}

function commitSessionDrop(account: string, sourceId: string, targetId: string, marker: 'before' | 'after' | null) {
  if (sourceId === targetId) return;
  const list = view.state.sessionOrderByAccount[account] ?? [];
  const targetIndex = list.indexOf(targetId);
  if (targetIndex < 0) return;
  const beforeId = marker === 'before' ? targetId : list[targetIndex + 1] ?? null;
  view.moveSessionBefore(account, sourceId, beforeId);
  if (view.state.orderBy !== 'manual') {
    view.state.orderBy = 'manual';
    ui.pushToast('已调整会话顺序并切换为手动排序', 'info');
  }
}

function onDragEnd() {
  drag.value = null;
}

// Global drag acceptance so dropping outside the row resets cleanly
function onDocumentDragOver(event: DragEvent) {
  if (drag.value) event.preventDefault();
}
onMounted(() => document.addEventListener('dragover', onDocumentDragOver));
onBeforeUnmount(() => document.removeEventListener('dragover', onDocumentDragOver));

// ---- search ----
const query = ref('');
const searchExpanded = ref(false);
const searchInput = ref<HTMLInputElement | null>(null);

const searchResults = computed(() => {
  const q = query.value.trim().toLowerCase();
  if (!q) return [];
  const hidden = new Set(registry.hiddenSessions);
  return store.sessionList
    .filter((s) => !hidden.has(s.id))
    .filter((s) => sessionTitle(s).toLowerCase().includes(q));
});

async function expandSearch() {
  searchExpanded.value = true;
  await nextTick();
  searchInput.value?.focus();
}

function clearSearch() {
  query.value = '';
  searchExpanded.value = false;
}

function onSearchKey(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault();
    clearSearch();
  }
}
function onSearchBlur() {
  if (!query.value.trim()) searchExpanded.value = false;
}
function onRailSearch() {
  emit('expandSidebar');
  setTimeout(() => void expandSearch(), 300);
}

// ---- view options menu (dsh: groupBy + orderBy; Bloom adds showArchived) ----
const viewMenuOpen = ref(false);
const viewMenuItems = computed<MenuEntry[]>(() => [
  { type: 'label', text: '分组方式' },
  { id: 'group:workspace', label: '按工作区' },
  { id: 'group:flat', label: '单列表' },
  { type: 'separator' },
  { type: 'label', text: '排序方式' },
  { id: 'order:manual', label: '手动排序' },
  { id: 'order:updated', label: '最近更新' },
  { type: 'separator' },
  {
    id: 'toggle:archived',
    label: view.state.showArchived ? '隐藏已归档会话' : '显示已归档会话',
  },
]);

const viewSelected = computed(() => [
  `group:${view.state.groupBy}`,
  `order:${view.state.orderBy}`,
  ...(view.state.showArchived ? ['toggle:archived'] : []),
]);

function onViewSelect(id: string) {
  if (id === 'group:workspace' || id === 'group:flat') view.setGroupBy(id.slice(6) as SessionGroupBy);
  else if (id === 'order:manual' || id === 'order:updated') view.setOrderBy(id.slice(6) as SessionOrderBy);
  else if (id === 'toggle:archived') view.setShowArchived(!view.state.showArchived);
  viewMenuOpen.value = false;
}

// ---- modals (rename workspace, delete workspace, rename session) ----
const renameWsTarget = ref<LocalWorkspace | null>(null);
const renameWsDraft = ref('');
const renameWsError = ref<string | null>(null);
const renameWsInput = ref<HTMLInputElement | null>(null);

function openRenameWorkspace(w: LocalWorkspace) {
  renameWsTarget.value = w;
  renameWsDraft.value = w.name;
  renameWsError.value = null;
  void nextTick(() => {
    renameWsInput.value?.focus();
    renameWsInput.value?.select();
  });
}

function commitRenameWorkspace() {
  const w = renameWsTarget.value;
  if (!w) return;
  const draft = renameWsDraft.value.trim();
  if (!draft) return;
  if (registry.workspaceNameTaken(draft, w.id)) {
    renameWsError.value = `已存在名为「${draft}」的工作区。`;
    return;
  }
  registry.renameWorkspace(w.id, draft);
  renameWsTarget.value = null;
}

const deleteWsTarget = ref<LocalWorkspace | null>(null);
function openDeleteWorkspace(w: LocalWorkspace) {
  deleteWsTarget.value = w;
}
function commitDeleteWorkspace() {
  const w = deleteWsTarget.value;
  if (!w) return;
  registry.removeWorkspace(w.id);
  deleteWsTarget.value = null;
}

const renameSessionTargetId = ref<string | null>(null);
const renameSessionDraft = ref('');
const renameSessionInput = ref<HTMLInputElement | null>(null);

function openRenameSession(id: string) {
  renameSessionTargetId.value = id;
  const s = store.sessionList.find((x) => x.id === id);
  renameSessionDraft.value = s ? sessionTitle(s) : '';
  void nextTick(() => {
    renameSessionInput.value?.focus();
    renameSessionInput.value?.select();
  });
}

function commitRenameSession() {
  const id = renameSessionTargetId.value;
  const draft = renameSessionDraft.value.trim();
  if (!id || !draft) return;
  rememberSessionTitle(id, draft);
  renameSessionTargetId.value = null;
}

// ---- session actions ----
function openSession(id: string) {
  if (id === store.currentSessionId) return;
  void selectSession(id);
}

function onArchiveSession(id: string) {
  registry.setHidden(id, true);
  if (store.currentSessionId === id) {
    const s = store.sessionList.find((x) => x.id === id);
    void startBlankSession(s?.cwd ?? null);
  }
}

function onUnarchiveSession(id: string) {
  registry.setHidden(id, false);
}

function newSessionIn(g: GroupModel) {
  void startBlankSession(g.path || null);
}

function addWorkspace() {
  ui.openWorkspaceDialog('new-session');
}

const sectionLabel = computed(() => (view.state.groupBy === 'flat' ? '会话' : '工作区'));
</script>

<template>
  <div :class="['browser', { rail: !wide }]">
    <!-- WIDE HEADER -->
    <template v-if="wide">
      <div class="sectionHeader">
        <span :class="['sectionLabel', 'wide', { sectionLabelHidden: searchExpanded }]">{{ sectionLabel }}</span>
        <div :class="['searchSlot', { searchSlotExpanded: searchExpanded }]">
          <div :class="['search', { searchExpanded }]" @click="expandSearch">
            <DsTooltip label="搜索" side="bottom" :disabled="searchExpanded">
              <button
                type="button"
                class="searchButton"
                aria-label="搜索会话"
                :aria-expanded="searchExpanded"
                @click.stop="expandSearch"
              >
                <IconSearch :size="searchExpanded ? 11 : 14" :stroke-width="ICON_STROKE" />
              </button>
            </DsTooltip>
            <input
              ref="searchInput"
              v-model="query"
              class="searchInput"
              type="text"
              placeholder="搜索会话…"
              maxlength="500"
              :tabindex="searchExpanded ? 0 : -1"
              @keydown="onSearchKey"
              @blur="onSearchBlur"
            />
            <button
              v-if="searchExpanded"
              type="button"
              class="clearButton"
              aria-label="清除搜索"
              @click.stop="clearSearch"
            >
              <IconClose :size="14" :stroke-width="ICON_STROKE" />
            </button>
          </div>
        </div>
        <div :class="['headerActions', { headerActionsHidden: searchExpanded }]">
          <DsMenu
            :open="viewMenuOpen"
            :items="viewMenuItems"
            :selected-ids="viewSelected"
            dense
            align="end"
            aria-label="视图选项"
            @select="onViewSelect"
            @close="viewMenuOpen = false"
          >
            <DsTooltip label="视图选项" side="bottom" :disabled="viewMenuOpen">
              <button
                type="button"
                class="iconButton wide"
                aria-label="视图选项"
                aria-haspopup="menu"
                :aria-expanded="viewMenuOpen"
                @click="viewMenuOpen = !viewMenuOpen"
              >
                <IconViewOptions :size="16" :stroke-width="ICON_STROKE" />
              </button>
            </DsTooltip>
          </DsMenu>
          <DsTooltip label="添加工作区" side="bottom">
            <button type="button" class="iconButton" aria-label="添加工作区" @click="addWorkspace">
              <IconProjectAdd :size="16" :stroke-width="ICON_STROKE" />
            </button>
          </DsTooltip>
        </div>
      </div>

      <!-- WIDE LIST -->
      <div class="listArea">
        <div class="treeBody wide">
          <div class="list" role="tree" aria-label="会话">
            <!-- SEARCH RESULTS -->
            <template v-if="query.trim()">
              <div v-if="searchResults.length === 0" class="empty">无匹配会话</div>
              <div v-else class="searchTree" role="tree" aria-label="搜索结果">
                <button
                  v-for="s in searchResults"
                  :key="s.id"
                  type="button"
                  :class="['searchResultRow', { selected: s.id === store.currentSessionId }]"
                  role="treeitem"
                  @click="openSession(s.id)"
                >
                  <span class="searchResultHeading">
                    <span class="searchResultTitle">{{ sessionTitle(s) }}</span>
                  </span>
                  <span class="searchResultMeta">
                    <span class="searchResultWorkspace">{{ s.cwd ? basename(s.cwd) : '未分组' }}</span>
                  </span>
                </button>
              </div>
            </template>

            <!-- GROUPED TREE -->
            <template v-else-if="view.state.groupBy === 'workspace'">
              <div v-if="groups.length === 0" class="empty">
                {{ store.sessionsLoaded ? '暂无工作区，点击上方「+」添加' : '正在加载…' }}
              </div>
              <div
                v-for="g in groups"
                :key="g.key"
                class="groupSection"
              >
                <ProjectRow
                  :label="g.label"
                  :path="g.path"
                  :expanded="view.isGroupExpanded(g.key)"
                  :contains-current="g.containsCurrent"
                  :ungrouped="g.ungrouped"
                  :draggable="!g.ungrouped"
                  :drop-marker="drag?.kind === 'workspace' && drag.targetId === g.workspaceId ? drag.marker : null"
                  @toggle="view.setGroupExpanded(g.key, !view.isGroupExpanded(g.key))"
                  @new-session="newSessionIn(g)"
                  @rename="g.workspaceId && openRenameWorkspace(registry.workspaces.find((w) => w.id === g.workspaceId)!)"
                  @delete="g.workspaceId && openDeleteWorkspace(registry.workspaces.find((w) => w.id === g.workspaceId)!)"
                  @dragstart="g.workspaceId && onWsDragStart($event, g.workspaceId)"
                  @dragover="g.workspaceId && onWsDragOver($event, g.workspaceId)"
                  @drop="g.workspaceId && onWsDrop($event, g.workspaceId)"
                  @dragend="onDragEnd"
                />
                <template v-if="view.isGroupExpanded(g.key)">
                  <!-- Pinned blank session row for the current workspace hero -->
                  <SessionRow
                    v-if="g.isCurrentBlankHere"
                    :session="{ id: '__blank__', createdAt: Date.now() }"
                    :selected="true"
                    :running="false"
                    :now="now"
                    :blank="true"
                    @open="() => {}"
                  />
                  <SessionRow
                    v-for="s in visibleSessions(g)"
                    :key="s.id"
                    :session="s"
                    :selected="s.id === store.currentSessionId"
                    :running="isRunning(s)"
                    :now="now"
                    :archived="registry.isHidden(s.id)"
                    :draggable="true"
                    :drop-marker="drag?.kind === 'session' && drag.account === g.key && drag.targetId === s.id ? drag.marker : null"
                    @open="openSession"
                    @rename="openRenameSession"
                    @archive="onArchiveSession"
                    @unarchive="onUnarchiveSession"
                    @dragstart="onSessionDragStart($event, g.key, s.id)"
                    @dragover="onSessionDragOver($event, g.key, s.id)"
                    @drop="onSessionDrop($event, g.key, s.id)"
                    @dragend="onDragEnd"
                  />
                  <button
                    v-if="hiddenCount(g) > 0"
                    type="button"
                    class="sessionOverflowButton"
                    @click="overflowOpen = { ...overflowOpen, [g.key]: true }"
                  >
                    展开其余 {{ hiddenCount(g) }} 个会话
                  </button>
                  <button
                    v-else-if="overflowOpen[g.key] && g.sessions.length > COLLAPSED_SESSION_LIMIT"
                    type="button"
                    class="sessionOverflowButton"
                    @click="overflowOpen = { ...overflowOpen, [g.key]: false }"
                  >
                    收起
                  </button>
                </template>
              </div>
            </template>

            <!-- FLAT LIST -->
            <template v-else>
              <div v-if="flatSessions.length === 0" class="empty">
                {{ store.sessionsLoaded ? '暂无会话' : '正在加载…' }}
              </div>
              <div v-else class="flatList">
                <SessionRow
                  v-if="!store.currentSessionId"
                  :session="{ id: '__blank__', createdAt: Date.now() }"
                  :selected="true"
                  :running="false"
                  :now="now"
                  :blank="true"
                  flat
                  @open="() => {}"
                />
                <SessionRow
                  v-for="s in flatSessions"
                  :key="s.id"
                  :session="s"
                  :selected="s.id === store.currentSessionId"
                  :running="isRunning(s)"
                  :now="now"
                  :archived="registry.isHidden(s.id)"
                  :draggable="true"
                  :drop-marker="drag?.kind === 'session' && drag.account === FLAT_ACCOUNT && drag.targetId === s.id ? drag.marker : null"
                  flat
                  @open="openSession"
                  @rename="openRenameSession"
                  @archive="onArchiveSession"
                  @unarchive="onUnarchiveSession"
                  @dragstart="onSessionDragStart($event, FLAT_ACCOUNT, s.id)"
                  @dragover="onSessionDragOver($event, FLAT_ACCOUNT, s.id)"
                  @drop="onSessionDrop($event, FLAT_ACCOUNT, s.id)"
                  @dragend="onDragEnd"
                />
              </div>
            </template>
          </div>
          <span class="fade" aria-hidden="true" />
        </div>
      </div>
    </template>

    <!-- COLLAPSED RAIL (strictly follows dsh) -->
    <template v-else>
      <div class="sectionHeader">
        <div class="headerActions">
          <DsTooltip label="添加工作区">
            <button type="button" class="iconButton" aria-label="添加工作区" @click="addWorkspace">
              <IconProjectAdd :size="18" :stroke-width="ICON_STROKE" />
            </button>
          </DsTooltip>
        </div>
      </div>
      <div class="search">
        <DsTooltip label="搜索">
          <button type="button" class="searchButton" aria-label="搜索会话" @click="onRailSearch">
            <IconSearch :size="18" :stroke-width="ICON_STROKE" />
          </button>
        </DsTooltip>
      </div>
      <div class="listArea" />
    </template>

    <!-- MODAL: Rename Workspace (dsh modal, Enter commits, duplicate check) -->
    <DsModal
      :open="renameWsTarget !== null"
      title="重命名工作区"
      @close="renameWsTarget = null"
    >
      <div class="modalField">
        <input
          ref="renameWsInput"
          v-model="renameWsDraft"
          type="text"
          class="modalInput"
          aria-label="工作区名称"
          placeholder="工作区名称"
          @keydown.enter.prevent="commitRenameWorkspace"
        />
        <p v-if="renameWsError" class="modalError">{{ renameWsError }}</p>
      </div>
      <template #footer>
        <DsButton variant="outline" @click="renameWsTarget = null">取消</DsButton>
        <DsButton variant="primary" :disabled="!renameWsDraft.trim()" @click="commitRenameWorkspace">重命名</DsButton>
      </template>
    </DsModal>

    <!-- MODAL: Delete Workspace (dsh: folder and session logs kept, move to Ungrouped) -->
    <DsModal
      :open="deleteWsTarget !== null"
      title="删除工作区"
      :description="`将从工作区列表中移除「${deleteWsTarget?.name || ''}」。文件夹与会话记录会保留，其会话将显示在「未分组」下。`"
      @close="deleteWsTarget = null"
    >
      <template #footer>
        <DsButton variant="outline" @click="deleteWsTarget = null">取消</DsButton>
        <DsButton variant="danger" @click="commitDeleteWorkspace">删除工作区</DsButton>
      </template>
    </DsModal>

    <!-- MODAL: Rename Session -->
    <DsModal
      :open="renameSessionTargetId !== null"
      title="重命名会话"
      @close="renameSessionTargetId = null"
    >
      <div class="modalField">
        <input
          ref="renameSessionInput"
          v-model="renameSessionDraft"
          type="text"
          class="modalInput"
          aria-label="会话名称"
          placeholder="会话名称"
          @keydown.enter.prevent="commitRenameSession"
        />
      </div>
      <template #footer>
        <DsButton variant="outline" @click="renameSessionTargetId = null">取消</DsButton>
        <DsButton variant="primary" :disabled="!renameSessionDraft.trim()" @click="commitRenameSession">重命名</DsButton>
      </template>
    </DsModal>
  </div>
</template>

<style scoped>
.browser {
  --dsh-session-list-edge-inset: var(--dsh-sidebar-inline-padding, 12px);
  --dsh-session-list-scrollbar-width: 8px;
  --dsh-session-list-scrollbar-offset: 2px;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  padding-right: var(--dsh-session-list-edge-inset);
}

.browser.rail {
  padding-right: 0;
}

.iconButton {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  corner-shape: round;
  padding: 0;
  background: transparent;
  cursor: pointer;
  color: var(--dsw-alias-label-secondary);
}

.iconButton:hover {
  background: var(--dsw-alias-interactive-bg-hover);
}

.sectionHeader {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  height: 36px;
  padding-left: 4px;
  margin-bottom: 4px;
  box-sizing: border-box;
  border-radius: 12px;
  overflow: hidden;
  color: var(--dsw-alias-label-tertiary);
}

.browser:not(.rail) .sectionHeader {
  margin-top: 2px;
  margin-right: -4px;
}

.sectionLabel {
  flex: none;
  max-width: 45%;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  line-height: 20px;
  opacity: 1;
  visibility: visible;
  transition:
    max-width 180ms var(--ds-ease-in-out),
    margin-right 180ms var(--ds-ease-in-out),
    opacity 120ms var(--ds-ease-in-out),
    transform 180ms var(--ds-ease-in-out),
    visibility 0s linear;
}

.sectionLabelHidden {
  max-width: 0;
  margin-right: -4px;
  opacity: 0;
  transform: translateX(-4px);
  visibility: hidden;
  transition-delay: 0s, 0s, 0s, 0s, 180ms;
}

.searchSlot {
  flex: 1;
  max-width: 28px;
  min-width: 0;
  display: flex;
  align-items: center;
  margin-left: auto;
  padding-left: 0;
  box-sizing: border-box;
  transition:
    max-width 180ms var(--ds-ease-in-out),
    padding-left 180ms var(--ds-ease-in-out);
}

.searchSlotExpanded {
  max-width: 100%;
}

.headerActions {
  flex: none;
  display: flex;
  align-items: center;
  gap: 4px;
  max-width: 60px;
  opacity: 1;
  overflow: hidden;
  visibility: visible;
  transition:
    max-width 180ms var(--ds-ease-in-out),
    opacity 120ms var(--ds-ease-in-out),
    transform 180ms var(--ds-ease-in-out),
    visibility 0s linear;
}

.headerActionsHidden {
  max-width: 0;
  opacity: 0;
  transform: translateX(4px);
  visibility: hidden;
  pointer-events: none;
  transition-delay: 0s, 0s, 0s, 180ms;
}

.search {
  flex: none;
  display: flex;
  align-items: center;
  gap: 0;
  width: 100%;
  height: 28px;
  margin: 0;
  padding: 0;
  box-sizing: border-box;
  border: none;
  border-radius: 50%;
  corner-shape: round;
  background: transparent;
  cursor: text;
  color: var(--dsw-alias-label-secondary);
  overflow: hidden;
  transition:
    width 180ms var(--ds-ease-in-out),
    padding 180ms var(--ds-ease-in-out),
    border-color 180ms var(--ds-ease-in-out),
    background-color 180ms var(--ds-ease-in-out);
}

.searchExpanded {
  width: calc(100% + 4px);
  height: 30px;
  margin-inline: -2px;
  padding: 0 4px 0 0;
  border: 0.5px solid var(--dsw-alias-border-l4);
  border-radius: 10px;
  corner-shape: initial;
  background: transparent;
  color: var(--dsw-alias-label-caption);
}

.searchButton {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  corner-shape: round;
  padding: 0;
  background: transparent;
  cursor: pointer;
  color: inherit;
}

.searchButton:hover {
  background: var(--dsw-alias-interactive-bg-hover);
}

.searchExpanded .searchButton {
  height: 30px;
}

.searchExpanded .searchButton:hover {
  background: transparent;
}

.searchInput {
  flex: 1;
  width: 0;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  opacity: 0;
  pointer-events: none;
  font-size: 13px;
  line-height: 18px;
  color: var(--dsw-alias-label-primary);
  transition: opacity 120ms var(--ds-ease-in-out);
}

.searchExpanded .searchInput {
  margin-left: -2px;
  opacity: 1;
  pointer-events: auto;
}

.searchInput::placeholder {
  color: var(--dsw-alias-label-tertiary);
}

.clearButton {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 50%;
  corner-shape: round;
  padding: 0;
  background: transparent;
  cursor: pointer;
  color: var(--dsw-alias-label-secondary);
}

.clearButton:hover {
  background: var(--dsw-alias-interactive-bg-hover);
}

/* rail overrides */
.rail .sectionHeader {
  gap: 0;
  padding-left: 0;
  margin-bottom: 12px;
  justify-content: flex-start;
}

.rail .headerActions {
  max-width: none;
}

.rail .iconButton,
.rail .search,
.rail .searchButton {
  width: 36px;
  height: 36px;
  color: var(--dsw-alias-label-primary);
}

.rail .search {
  margin: 0 0 12px;
  border-color: transparent;
}

.rail .searchButton:hover {
  background: var(--dsw-alias-interactive-bg-hover);
}

/* list region */
.listArea {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  margin-left: -4px;
  margin-right: calc(-1 * var(--dsh-session-list-edge-inset));
  padding-left: 4px;
  overflow: visible;
}

.rail .listArea {
  margin: 0;
  padding: 0;
}

.treeBody {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  position: relative;
}

.fade {
  position: absolute;
  left: 0;
  right: var(--dsh-session-list-edge-inset);
  bottom: 0;
  height: 24px;
  background: linear-gradient(to bottom, transparent, var(--dsw-specific-sidebar-fill));
  pointer-events: none;
}

.wide {
  animation: wide-in 200ms var(--ds-ease-in-out);
}

@keyframes wide-in {
  from {
    opacity: 0;
  }
}

.list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  margin-left: -4px;
  margin-right: var(--dsh-session-list-scrollbar-offset);
  padding-left: 4px;
  padding-right: calc(
    var(--dsh-session-list-edge-inset) - var(--dsh-session-list-scrollbar-width) - var(--dsh-session-list-scrollbar-offset)
  );
  padding-bottom: 16px;
  scrollbar-gutter: stable;
}

.flatList > * + *,
.searchTree > * + *,
.groupSection > * + * {
  margin-top: 2px;
}

.groupSection {
  position: relative;
}

.groupSection + .groupSection {
  margin-top: 4px;
}

.sessionOverflowButton {
  width: 100%;
  height: 28px;
  border: none;
  border-radius: 8px;
  padding: 0 12px 0 28px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-size: 12px;
  color: var(--dsw-alias-label-tertiary);
}

.sessionOverflowButton:hover {
  color: var(--dsw-alias-label-secondary);
}

.groupSection > .sessionOverflowButton {
  margin-top: 0;
}

.empty {
  padding: 16px 12px;
  color: var(--dsw-alias-label-tertiary);
  font-size: 13px;
}

/* search results (dsh SearchResultItem) */
.searchResultRow {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  width: 100%;
  min-height: 48px;
  box-sizing: border-box;
  border: none;
  border-radius: 8px;
  padding: 4px 8px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  color: var(--dsw-alias-label-primary);
}

.searchResultRow:hover,
.searchResultRow.selected {
  background: var(--dsw-alias-interactive-bg-hover);
}

.searchResultHeading {
  display: flex;
  align-items: center;
  min-width: 0;
}

.searchResultTitle {
  flex: 0 1 auto;
  min-width: 0;
  margin-left: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  line-height: 20px;
}

.searchResultMeta {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  margin-left: 4px;
}

.searchResultWorkspace {
  flex: none;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  line-height: 17px;
  color: var(--dsw-alias-label-tertiary);
}

/* Modals */
.modalField {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.modalInput {
  box-sizing: border-box;
  width: 100%;
  height: 38px;
  padding: 8px 12px;
  border: 1px solid var(--dsw-alias-border-l2);
  border-radius: 12px;
  background: var(--dsw-alias-bg-layer-1);
  color: var(--dsw-alias-label-primary);
  font-size: 14px;
  line-height: 22px;
  outline: none;
}

.modalInput:focus {
  border-color: var(--dsw-alias-border-l1);
  box-shadow: 0 0 0 2px var(--dsw-alias-border-l3);
}

.modalError {
  margin: 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--dsw-alias-state-error-primary);
}

@media (prefers-reduced-motion: reduce) {
  .wide {
    animation: none;
  }

  .search,
  .sectionLabel,
  .searchSlot,
  .searchInput,
  .headerActions {
    transition: none;
  }
}
</style>
