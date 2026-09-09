import { defineStore } from 'pinia';
import { ref } from 'vue';

export type ConnectionState = 'disconnected' | 'connecting' | 'recovered' | undefined;

export interface ToastItem {
  id: number;
  text: string;
  tone: 'warning' | 'info';
}

/** Which workspace dialog flow is open and what "Open" should do. */
export type WorkspaceDialogMode =
  /** Pick the folder for the blank (hero) session. */
  | 'hero'
  /** Change the current session's working directory (PUT /cwd). */
  | 'session-cwd'
  /** Start a new blank session in the picked folder. */
  | 'new-session';

export const SIDEBAR_DEFAULT = 280;
export const SIDEBAR_MIN = 264;
export const SIDEBAR_MAX = 420;
export const SIDEBAR_COLLAPSED = 56;
export const SIDEBAR_AUTO_COLLAPSE = 1024;

const RECOVERY_CONFIRMATION_MS = 2000;

function clamp(px: number, min: number, max: number) {
  return Math.min(max, Math.max(min, Math.round(px)));
}

/**
 * Shell/UI state (port of dsh ui-layout stores + toast/overlay seats):
 * sidebar geometry, open dialogs, toasts and the connection indicator.
 */
export const useUiStore = defineStore('ui', () => {
  // ---- layout (dsh ui-layout/stores.ts) ----
  /** Sidebar preference in px; 0 = closed (the preference IS the width). */
  const sidebar = ref(SIDEBAR_DEFAULT);
  const narrow = ref(false);
  const narrowExpanded = ref(false);

  function setSidebar(px: number) {
    sidebar.value = clamp(px, SIDEBAR_MIN, SIDEBAR_MAX);
  }
  function toggleSidebar() {
    if (narrow.value) {
      narrowExpanded.value = !narrowExpanded.value;
    } else {
      sidebar.value = sidebar.value === 0 ? SIDEBAR_DEFAULT : 0;
    }
  }
  function setNarrow(next: boolean) {
    if (narrow.value === next) return;
    narrow.value = next;
    narrowExpanded.value = false;
  }

  // ---- dialogs ----
  const settingsOpen = ref(false);
  const settingsSection = ref<'general' | 'models'>('general');
  function openSettings(section: 'general' | 'models' = 'general') {
    settingsSection.value = section;
    settingsOpen.value = true;
  }
  function closeSettings() {
    settingsOpen.value = false;
  }

  const workspaceDialogMode = ref<WorkspaceDialogMode | null>(null);
  function openWorkspaceDialog(mode: WorkspaceDialogMode) {
    workspaceDialogMode.value = mode;
  }
  function closeWorkspaceDialog() {
    workspaceDialogMode.value = null;
  }

  // ---- toasts ----
  const toasts = ref<ToastItem[]>([]);
  let toastSeq = 0;
  function pushToast(text: string, tone: ToastItem['tone'] = 'warning') {
    const id = ++toastSeq;
    toasts.value = [...toasts.value.slice(-2), { id, text, tone }];
    return id;
  }
  function dismissToast(id: number) {
    toasts.value = toasts.value.filter((t) => t.id !== id);
  }

  // ---- connection indicator (dsh ConnectionIndicator states) ----
  const connection = ref<ConnectionState>(undefined);
  let recoveryTimer: ReturnType<typeof setTimeout> | null = null;

  function reportBackendFailure() {
    if (recoveryTimer) {
      clearTimeout(recoveryTimer);
      recoveryTimer = null;
    }
    connection.value = 'disconnected';
  }
  function reportBackendReconnecting() {
    if (connection.value === 'disconnected') connection.value = 'connecting';
  }
  function reportBackendOk() {
    if (connection.value === undefined) return;
    if (connection.value === 'recovered') return;
    connection.value = 'recovered';
    recoveryTimer = setTimeout(() => {
      if (connection.value === 'recovered') connection.value = undefined;
      recoveryTimer = null;
    }, RECOVERY_CONFIRMATION_MS);
  }

  return {
    sidebar,
    narrow,
    narrowExpanded,
    setSidebar,
    toggleSidebar,
    setNarrow,
    settingsOpen,
    settingsSection,
    openSettings,
    closeSettings,
    workspaceDialogMode,
    openWorkspaceDialog,
    closeWorkspaceDialog,
    toasts,
    pushToast,
    dismissToast,
    connection,
    reportBackendFailure,
    reportBackendReconnecting,
    reportBackendOk,
  };
});
