<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { useAgentStore } from '@/stores/agentStore';
import { useUiStore } from '@/stores/uiStore';
import { useSession } from '@/composables/useSession';
import { useWorkspace } from '@/composables/useWorkspace';
import { useWorkspaceRegistry } from '@/composables/useWorkspaceRegistry';
import type { DirectoryListing } from '@/types/session.types';
import DsButton from '@/components/primitives/DsButton.vue';
import DsTooltip from '@/components/primitives/DsTooltip.vue';
import {
  IconChevronRight,
  IconEdit,
  IconFolderClose,
  IconFolderOpen,
  IconProjectAdd,
  ICON_STROKE,
} from '@/components/primitives/icons';
import { basename, pathSegments } from '@/utils/format';

/**
 * dsh DirectoryBrowser adapted to the BloomHarness workspace API: a 680 × 500
 * Miller-column folder picker — breadcrumb trail with click-to-edit path,
 * current level on the left, selection's children on the right, and a footer
 * with the OS picker, Cancel and Open.
 *
 * Folder choice immediately adds the path to the workspace registry so it
 * shows up in the left sidebar right away, even before a session is started.
 */
const store = useAgentStore();
const ui = useUiStore();
const { startBlankSession, updateSessionCwd, selectSession } = useSession();
const { browse, validate, pickSystemFolder, loadWorkspaceInfo, workspaceInfo } = useWorkspace();
const registry = useWorkspaceRegistry();

const open = computed(() => ui.workspaceDialogMode !== null);

interface Entry {
  name: string;
  path: string;
}

const parent = ref<DirectoryListing | null>(null);
const selected = ref<Entry | null>(null);
const child = ref<DirectoryListing | null>(null);
const loading = ref(false);
const slowLoading = ref(false);
const error = ref<string | null>(null);
const busy = ref(false);

// System picker countdown: Spring MVC times out at 30s on the backend
const picking = ref(false);
const pickingSeconds = ref(30);
let pickTimer: ReturnType<typeof setInterval> | null = null;

const pathDraft = ref<string | null>(null);
const pathInput = ref<HTMLInputElement | null>(null);

let scanController: AbortController | null = null;
let slowTimer: ReturnType<typeof setTimeout> | null = null;
const SLOW_SCAN_DELAY_MS = 300;

function beginScan(): AbortSignal {
  scanController?.abort();
  scanController = new AbortController();
  loading.value = true;
  error.value = null;
  if (slowTimer) clearTimeout(slowTimer);
  slowTimer = setTimeout(() => (slowLoading.value = true), SLOW_SCAN_DELAY_MS);
  return scanController.signal;
}

function endScan() {
  loading.value = false;
  slowLoading.value = false;
  if (slowTimer) {
    clearTimeout(slowTimer);
    slowTimer = null;
  }
}

function failureText(e: unknown): string {
  if (e instanceof Error) return e.message;
  return String(e);
}

const targetPath = computed(() => selected.value?.path ?? parent.value?.currentPath ?? '');

/** Land on `path`: its parent level on the left with `path` selected, its children on the right. */
async function navigate(path: string) {
  const signal = beginScan();
  try {
    const listing = await browse(path, signal);
    if (signal.aborted) return;
    if (listing.parentPath) {
      const [parentListing] = await Promise.all([browse(listing.parentPath, signal)]);
      if (signal.aborted) return;
      parent.value = parentListing;
      selected.value = { name: basename(listing.currentPath) || listing.currentPath, path: listing.currentPath };
      child.value = listing;
    } else {
      parent.value = listing;
      selected.value = null;
      child.value = null;
    }
  } catch (e) {
    if (!signal.aborted) error.value = failureText(e);
  } finally {
    if (!signal.aborted) endScan();
  }
}

async function select(entry: Entry) {
  selected.value = entry;
  child.value = null;
  const signal = beginScan();
  try {
    const listing = await browse(entry.path, signal);
    if (signal.aborted) return;
    child.value = listing;
  } catch (e) {
    if (!signal.aborted) error.value = failureText(e);
  } finally {
    if (!signal.aborted) endScan();
  }
}

async function advance(entry: Entry) {
  if (!child.value) return;
  parent.value = child.value;
  await select(entry);
}

const crumbs = computed(() => {
  const path = targetPath.value;
  if (!path) return [];
  const segments = pathSegments(path);
  const home = workspaceInfo.value?.userHome;
  if (home) {
    const homeSegs = pathSegments(home);
    const homePath = homeSegs[homeSegs.length - 1]?.path;
    const idx = segments.findIndex((s) => normalize(s.path) === normalize(homePath ?? ''));
    if (idx >= 0) {
      return [{ label: '主目录', path: segments[idx]!.path }, ...segments.slice(idx + 1)];
    }
  }
  return segments;
});

function normalize(p: string) {
  return p.replace(/\\/g, '/').replace(/\/+$/, '').toLowerCase();
}

// ---- path editing (Enter commits, Esc cancels, blur commits if changed) ----
let initialDraft = '';
async function startEdit() {
  const sep = workspaceInfo.value?.fileSeparator ?? (targetPath.value.includes('\\') ? '\\' : '/');
  const base = targetPath.value;
  pathDraft.value = base.endsWith(sep) ? base : base + sep;
  initialDraft = pathDraft.value;
  await nextTick();
  pathInput.value?.focus();
  pathInput.value?.setSelectionRange(pathDraft.value.length, pathDraft.value.length);
}

function cancelEdit() {
  pathDraft.value = null;
}

async function submitEdit() {
  const draft = (pathDraft.value ?? '').trim();
  if (!draft) {
    cancelEdit();
    return;
  }
  const result = await validate(draft);
  if (!result.valid || !result.canonicalPath) {
    error.value = result.error || '路径无效';
    return;
  }
  pathDraft.value = null;
  await navigate(result.canonicalPath);
}

function onBlur() {
  const draft = (pathDraft.value ?? '').trim();
  if (!draft || draft === initialDraft.trim()) {
    cancelEdit();
  } else {
    void submitEdit();
  }
}

function onEditKey(event: KeyboardEvent) {
  if (event.isComposing) return;
  if (event.key === 'Enter') {
    event.preventDefault();
    void submitEdit();
  } else if (event.key === 'Escape') {
    event.preventDefault();
    event.stopPropagation();
    cancelEdit();
  }
}

// ---- OS picker (backend has 30s async timeout; dialog opens on server host) ----
function stopPickTimer() {
  if (pickTimer) {
    clearInterval(pickTimer);
    pickTimer = null;
  }
}

async function pickWithSystem() {
  picking.value = true;
  pickingSeconds.value = 30;
  error.value = null;
  stopPickTimer();
  pickTimer = setInterval(() => {
    if (pickingSeconds.value > 0) pickingSeconds.value--;
  }, 1000);

  try {
    const path = await pickSystemFolder(targetPath.value);
    if (path) {
      const result = await validate(path);
      if (!result.valid || !result.canonicalPath) {
        error.value = result.error || '系统选择的路径无效';
        return;
      }
      registry.addWorkspace(result.canonicalPath, result.name);
      await navigate(result.canonicalPath);
    } else {
      ui.pushToast('未选择文件夹', 'info');
    }
  } catch (e: any) {
    const msg = failureText(e);
    if (/超时|timeout|503/i.test(msg)) {
      error.value = '系统选择器等待超时（后端限制约 30 秒），请在上方浏览或粘贴路径。';
    } else {
      error.value = msg || '系统文件夹选择器调用失败，请在上方浏览。';
    }
  } finally {
    picking.value = false;
    stopPickTimer();
  }
}

// ---- confirm / close ----
async function confirm() {
  const path = targetPath.value;
  if (!path || busy.value) return;
  busy.value = true;
  error.value = null;
  try {
    const result = await validate(path);
    if (!result.valid || !result.canonicalPath) {
      error.value = result.error || '路径无效';
      return;
    }
    const canonical = result.canonicalPath;
    // Always add to the workspace registry so it shows on the left immediately
    registry.addWorkspace(canonical, result.name);

    const mode = ui.workspaceDialogMode;
    if (mode === 'hero') {
      store.blankSessionCwd = canonical;
    } else if (mode === 'new-session') {
      await startBlankSession(canonical);
    } else if (mode === 'session-cwd' && store.currentSessionId) {
      const ok = await updateSessionCwd(store.currentSessionId, canonical);
      if (!ok) {
        error.value = '更新会话工作区失败';
        return;
      }
      await selectSession(store.currentSessionId);
    }
    ui.closeWorkspaceDialog();
  } catch (e) {
    error.value = failureText(e);
  } finally {
    busy.value = false;
  }
}

function close() {
  ui.closeWorkspaceDialog();
}

function onKeyDown(event: KeyboardEvent) {
  if (event.key === 'Escape' && pathDraft.value === null) {
    event.stopPropagation();
    close();
  }
}

watch(open, async (isOpen) => {
  if (isOpen) {
    document.addEventListener('keydown', onKeyDown, true);
    parent.value = null;
    selected.value = null;
    child.value = null;
    error.value = null;
    pathDraft.value = null;
    const info = await loadWorkspaceInfo();
    const mode = ui.workspaceDialogMode;
    const initial =
      (mode === 'session-cwd' ? store.currentSession?.cwd : null) ||
      store.blankSessionCwd ||
      info?.defaultWorkspace ||
      undefined;
    if (initial) await navigate(initial);
    else {
      const signal = beginScan();
      try {
        const listing = await browse(undefined, signal);
        if (!signal.aborted) parent.value = listing;
      } catch (e) {
        if (!signal.aborted) error.value = failureText(e);
      } finally {
        if (!signal.aborted) endScan();
      }
    }
  } else {
    document.removeEventListener('keydown', onKeyDown, true);
    scanController?.abort();
    endScan();
    stopPickTimer();
    picking.value = false;
  }
});

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeyDown, true);
  scanController?.abort();
  if (slowTimer) clearTimeout(slowTimer);
  stopPickTimer();
});

const title = computed(() => (ui.workspaceDialogMode === 'session-cwd' ? '切换工作区目录' : '选择工作区目录'));
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="pickerRoot" role="presentation">
      <div class="mask" aria-hidden="true" @click="close" />
      <div class="dialog" role="dialog" aria-modal="true" :aria-label="title">
        <div class="header">
          <h2 class="title">{{ title }}</h2>
          <div class="crumbBar" :class="{ editing: pathDraft !== null }">
            <template v-if="pathDraft === null">
              <span class="crumbTrail" role="navigation">
                <span v-for="(crumb, i) in crumbs" :key="crumb.path" class="crumbSeat">
                  <IconChevronRight v-if="i > 0" class="crumbChevron" :size="12" :stroke-width="ICON_STROKE" />
                  <button type="button" class="crumb" :title="crumb.path" :disabled="busy" @click="navigate(crumb.path)">
                    {{ crumb.label }}
                  </button>
                </span>
              </span>
              <button type="button" class="crumbEditZone" aria-label="编辑路径" title="编辑路径" :disabled="busy" @click="startEdit">
                <IconEdit class="crumbEditGlyph" :size="14" :stroke-width="ICON_STROKE" />
              </button>
            </template>
            <input
              v-else
              ref="pathInput"
              v-model="pathDraft"
              class="pathInput"
              type="text"
              aria-label="编辑路径"
              spellcheck="false"
              @keydown="onEditKey"
              @blur="onBlur"
            />
          </div>
        </div>

        <div class="content">
          <div class="millerRow">
            <div class="column" role="list">
              <span v-for="entry in parent?.directories ?? []" :key="entry.path" class="rowSeat" role="listitem">
                <button
                  type="button"
                  :class="['row', { rowSelected: selected?.path === entry.path }]"
                  :aria-current="selected?.path === entry.path ? 'true' : undefined"
                  :disabled="busy"
                  @click="select(entry)"
                >
                  <IconFolderOpen v-if="selected?.path === entry.path" class="rowIconSelected" :size="16" :stroke-width="ICON_STROKE" />
                  <IconFolderClose v-else class="rowIcon" :size="16" :stroke-width="ICON_STROKE" />
                  <span class="rowName">{{ entry.name }}</span>
                  <IconChevronRight class="rowChevron" :size="12" :stroke-width="ICON_STROKE" />
                </button>
              </span>
              <span v-if="parent && parent.directories.length === 0" class="emptyHint">此目录下没有子文件夹</span>
            </div>
            <template v-if="selected && child">
              <span class="divider" aria-hidden="true" />
              <div class="column" role="list">
                <span v-for="entry in child.directories" :key="entry.path" class="rowSeat" role="listitem">
                  <button type="button" class="row" :disabled="busy" @click="advance(entry)">
                    <IconFolderClose class="rowIcon" :size="16" :stroke-width="ICON_STROKE" />
                    <span class="rowName">{{ entry.name }}</span>
                    <IconChevronRight class="rowChevron" :size="12" :stroke-width="ICON_STROKE" />
                  </button>
                </span>
                <span v-if="child.directories.length === 0" class="emptyHint">此目录下没有子文件夹</span>
              </div>
            </template>
          </div>
          <div v-if="loading && slowLoading" class="status loadingFloat" role="status">加载中…</div>
          <div v-if="error" class="error" role="alert">{{ error }}</div>
        </div>

        <div class="footerBar">
          <DsTooltip label="在服务端弹窗选择（30 秒超时，若未看到请检查任务栏）" side="top">
            <DsButton variant="outline" :disabled="picking || busy" @click="pickWithSystem">
              <template #icon><IconProjectAdd :size="14" :stroke-width="ICON_STROKE" /></template>
              {{ picking ? `等待系统选择… (${pickingSeconds}s)` : '系统文件夹选择器' }}
            </DsButton>
          </DsTooltip>
          <span class="targetPath" :title="targetPath">{{ targetPath }}</span>
          <span class="footerGap" />
          <DsButton variant="outline" class="footerAction" @click="close">取消</DsButton>
          <DsButton variant="primary" class="footerAction" :disabled="!targetPath || loading || busy" @click="confirm">
            {{ busy ? '打开中…' : '打开' }}
          </DsButton>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.pickerRoot {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.mask {
  position: absolute;
  inset: 0;
  background: var(--dsw-alias-bg-mask-1);
  backdrop-filter: var(--dsw-mask-blur);
}

.dialog {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  width: min(680px, 100%);
  height: min(500px, calc(100dvh - 32px));
  box-sizing: border-box;
  padding: 0;
  overflow: hidden;
  border: 0;
  border-radius: 24px;
  background: var(--dsw-alias-bg-layer-2);
  box-shadow: var(--dsw-elevation-prominent);
  --dsh-scrollbar-thumb: var(--dsw-alias-scrollbar-bg-l2);
  --dsh-scrollbar-thumb-hover: var(--dsw-alias-scrollbar-hover-l2);
}

.header {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: none;
  padding: 16px 14px 8px 24px;
  border-bottom: 0.5px solid var(--dsw-alias-border-l3);
}

.title {
  display: flex;
  align-items: flex-end;
  min-height: 28px;
  margin: 0;
  font-size: 16px;
  line-height: 24px;
  font-weight: 500;
  color: var(--dsw-alias-label-primary);
}

.crumbBar {
  display: flex;
  align-items: center;
  gap: 4px;
  box-sizing: border-box;
  min-height: 24px;
  margin-left: -9px;
  padding: 0 8px;
  border: 1px solid transparent;
  border-radius: 8px;
}

.crumbBar:has(.crumbEditZone:enabled:hover),
.crumbBar:has(.crumbEditZone:focus-visible),
.crumbBar.editing {
  border-color: var(--dsw-alias-border-l2);
}

.crumbTrail {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 0 1 auto;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}

.crumbTrail::-webkit-scrollbar {
  display: none;
}

.crumbSeat {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: none;
  min-width: 0;
}

.crumb {
  border: none;
  background: transparent;
  padding: 0;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  line-height: 20px;
  font-weight: 500;
  color: var(--dsw-alias-label-tertiary);
  cursor: pointer;
}

.crumb:hover:not(:disabled) {
  color: var(--dsw-alias-label-primary);
}

.crumbSeat:last-child .crumb {
  color: var(--dsw-alias-label-primary);
}

.crumbChevron {
  flex: none;
  color: var(--dsw-alias-label-tertiary);
}

.crumbEditZone {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex: 1 0 34px;
  min-width: 34px;
  height: 22px;
  padding: 0;
  border: none;
  background: transparent;
  cursor: text;
  outline: none;
}

.crumbEditGlyph {
  flex: none;
  color: var(--dsw-alias-label-tertiary);
}

.crumbEditZone:enabled:hover .crumbEditGlyph,
.crumbEditZone:focus-visible .crumbEditGlyph {
  color: var(--dsw-alias-label-primary);
}

.pathInput {
  box-sizing: border-box;
  flex: 1 1 0;
  min-width: 0;
  height: 22px;
  padding: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: 13px;
  line-height: 20px;
  font-family: var(--ds-font-family-code);
  color: var(--dsw-alias-label-primary);
}

.content {
  display: flex;
  flex-direction: column;
  flex: 1 1 0;
  min-height: 0;
  position: relative;
  padding: 16px 16px 16px 24px;
}

.millerRow {
  display: flex;
  align-items: stretch;
  flex: 1 1 0;
  min-height: 0;
  gap: 12px;
  overflow-x: auto;
}

.column {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1 1 0;
  min-width: 256px;
  overflow-y: auto;
  padding-right: 8px;
}

.divider {
  flex: none;
  width: 0.5px;
  background: var(--dsw-alias-border-l3);
}

.rowSeat {
  display: flex;
  flex: none;
}

.row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  flex: none;
  padding: 4px;
  box-sizing: border-box;
  border: none;
  border-radius: 6px;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.row:hover:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-hover);
}

.rowSelected,
.rowSelected:hover:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-active, var(--dsw-alias-interactive-bg-hover));
}

.rowIcon {
  flex: none;
  color: var(--dsw-alias-label-secondary);
}

.rowIconSelected {
  flex: none;
  color: var(--dsw-alias-button-info-fill);
}

.rowName {
  flex: 1 1 0;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  line-height: 20px;
  font-weight: 500;
  color: var(--dsw-alias-label-primary);
}

.rowChevron {
  flex: none;
  color: var(--dsw-alias-label-tertiary);
}

.emptyHint {
  padding: 8px 4px;
  font-size: 12px;
  line-height: 18px;
  color: var(--dsw-alias-label-tertiary);
}

.status,
.error {
  padding: 4px;
  padding-right: 120px;
  font-size: 12px;
  line-height: 18px;
}

.status {
  color: var(--dsw-alias-label-secondary);
}

.error {
  color: var(--dsw-alias-state-error-primary);
}

.loadingFloat {
  position: absolute;
  right: 16px;
  bottom: 8px;
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--dsw-alias-bg-layer-2);
}

.footerBar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  flex: none;
  padding: 16px 24px;
  border-top: 0.5px solid var(--dsw-alias-border-l3);
}

.targetPath {
  min-width: 0;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--ds-font-family-code);
  font-size: 12px;
  line-height: 18px;
  color: var(--dsw-alias-label-tertiary);
}

.footerGap {
  flex: 1 1 0;
}

.footerAction {
  min-width: 72px;
}
</style>
