<script setup lang="ts">
import { ref } from 'vue';
import SessionSelector from '@/components/SessionSelector.vue';
import ChatPanel from '@/components/ChatPanel.vue';
import SettingsModal from '@/components/SettingsModal.vue';
import WorkspaceModal from '@/components/WorkspaceModal.vue';
import { useAgentStore } from '@/stores/agentStore';
import { useSession } from '@/composables/useSession';

const store = useAgentStore();
const { createSession, updateSessionCwd, selectSession } = useSession();

const showSettings = ref(false);
const showWorkspaceModal = ref(false);

async function handleSelectWorkspace(path: string, createNewSession: boolean) {
  if (createNewSession || !store.currentSessionId) {
    const dirName = path.split('/').filter(Boolean).pop() || '工作区';
    await createSession(`${dirName} 会话`, path);
  } else {
    await updateSessionCwd(store.currentSessionId, path);
    if (store.currentSessionId) {
      await selectSession(store.currentSessionId);
    }
  }
}
</script>

<template>
  <div class="flex h-screen w-screen overflow-hidden bg-gray-950 font-sans">
    <SessionSelector 
      @open-settings="showSettings = true" 
      @open-workspace="showWorkspaceModal = true" 
    />
    <div class="flex-1 flex flex-col min-w-0">
      <ChatPanel 
        @open-settings="showSettings = true" 
        @open-workspace="showWorkspaceModal = true" 
      />
    </div>

    <!-- AI Router Settings Modal -->
    <SettingsModal v-if="showSettings" @close="showSettings = false" />

    <!-- Workspace Selector Modal -->
    <WorkspaceModal 
      v-if="showWorkspaceModal" 
      :current-cwd="store.currentSession?.cwd"
      @close="showWorkspaceModal = false"
      @select-workspace="handleSelectWorkspace"
    />
  </div>
</template>
