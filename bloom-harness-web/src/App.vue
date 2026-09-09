<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue';
import { useAgentStore } from '@/stores/agentStore';
import { useSession } from '@/composables/useSession';
import { useWorkspace } from '@/composables/useWorkspace';
import AppFrame from '@/components/layout/AppFrame.vue';
import SidebarRoot from '@/components/layout/SidebarRoot.vue';
import ConversationRoot from '@/components/conversation/ConversationRoot.vue';
import SettingsPanel from '@/components/settings/SettingsPanel.vue';
import DirectoryBrowserModal from '@/components/workspace/DirectoryBrowserModal.vue';
import DsToastHost from '@/components/primitives/DsToastHost.vue';

const store = useAgentStore();
const { fetchSessions, startBlankSession, selectSession } = useSession();
const { loadWorkspaceInfo } = useWorkspace();

// Refresh the session list when the window regains focus (replaces the manual refresh button).
function onFocus() {
  if (document.visibilityState === 'visible') void fetchSessions();
}

// The current session lives in the URL (`?session=<id>`) so a reload lands on the same conversation.
watch(
  () => store.currentSessionId,
  (id) => {
    const url = new URL(window.location.href);
    if (id) url.searchParams.set('session', id);
    else url.searchParams.delete('session');
    window.history.replaceState(null, '', url);
  },
);

onMounted(async () => {
  void loadWorkspaceInfo();
  const list = await fetchSessions();
  const requested = new URLSearchParams(window.location.search).get('session');
  if (requested && list.some((s) => s.id === requested)) {
    await selectSession(requested);
  }
  // Otherwise land on the blank hero (dsh "New Session") rather than auto-creating a session.
  if (!store.currentSessionId) await startBlankSession();
  window.addEventListener('focus', onFocus);
});

onBeforeUnmount(() => {
  window.removeEventListener('focus', onFocus);
});
</script>

<template>
  <AppFrame>
    <template #sidebar="{ collapsed, width }">
      <SidebarRoot :collapsed="collapsed" :width="width" />
    </template>
    <ConversationRoot />
  </AppFrame>

  <SettingsPanel />
  <DirectoryBrowserModal />
  <DsToastHost />
</template>
