<script setup lang="ts">
import { computed, ref } from 'vue';
import { useAgentStore } from '@/stores/agentStore';
import { useUiStore } from '@/stores/uiStore';
import { useWorkspace } from '@/composables/useWorkspace';
import DsMenu from '@/components/primitives/DsMenu.vue';
import type { MenuEntry } from '@/components/primitives/menu.types';
import { IconChevronDown, IconFolderClose, IconFolderOpen, IconProjectAdd, ICON_STROKE } from '@/components/primitives/icons';
import { abbreviateHome, basename } from '@/utils/format';

/**
 * dsh WorkspaceChip + WorkspacePicker: the hero's folder chip. Its menu lists
 * recent workspaces and a pinned "浏览文件夹…" entry that opens the directory browser.
 */
const store = useAgentStore();
const ui = useUiStore();
const { fetchRecentWorkspaces, workspaceInfo, loadWorkspaceInfo } = useWorkspace();

const open = ref(false);
const recents = ref<string[]>([]);
const loading = ref(false);

const label = computed(() => (store.blankSessionCwd ? basename(store.blankSessionCwd) || store.blankSessionCwd : '选择工作区'));

const items = computed<MenuEntry[]>(() => {
  if (loading.value && recents.value.length === 0) return [{ type: 'label', text: '正在加载工作区…' }];
  if (recents.value.length === 0) return [{ type: 'label', text: '暂无最近使用的工作区' }];
  return recents.value.map((path) => ({
    id: `ws:${path}`,
    label: basename(path) || path,
    icon: IconFolderClose,
    hint: abbreviateHome(path, workspaceInfo.value?.userHome),
  }));
});

const footer: MenuEntry[] = [{ id: 'browse', label: '浏览文件夹…', icon: IconProjectAdd }];

async function toggle() {
  open.value = !open.value;
  if (open.value) {
    loading.value = true;
    void loadWorkspaceInfo();
    recents.value = await fetchRecentWorkspaces();
    loading.value = false;
  }
}

function onSelect(id: string) {
  open.value = false;
  if (id === 'browse') {
    ui.openWorkspaceDialog('hero');
    return;
  }
  if (id.startsWith('ws:')) store.blankSessionCwd = id.slice(3);
}
</script>

<template>
  <DsMenu
    :open="open"
    :items="items"
    :footer="footer"
    :selected-id="store.blankSessionCwd ? `ws:${store.blankSessionCwd}` : null"
    :min-width="260"
    aria-label="选择工作区"
    @select="onSelect"
    @close="open = false"
  >
    <button
      type="button"
      class="workspace"
      aria-label="选择工作区"
      aria-haspopup="menu"
      :aria-expanded="open"
      @click="toggle"
    >
      <IconFolderOpen v-if="store.blankSessionCwd" class="folder" :size="16" :stroke-width="ICON_STROKE" />
      <IconFolderClose v-else class="folder" :size="16" :stroke-width="ICON_STROKE" />
      <span class="workspaceLabel">{{ label }}</span>
      <IconChevronDown class="chevron" :size="12" :stroke-width="ICON_STROKE" />
    </button>
  </DsMenu>
</template>

<style scoped>
.workspace {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: min(100%, 360px);
  min-height: 28px;
  padding: 0 8px;
  border: none;
  border-radius: 16px;
  background: transparent;
  color: var(--dsw-alias-label-primary);
  font-size: 13px;
  line-height: 20px;
  font-weight: 500;
  cursor: pointer;
}

.workspace:hover,
.workspace[aria-expanded='true'] {
  background: var(--dsw-alias-interactive-bg-hover);
}

.folder {
  flex: none;
  color: var(--dsw-alias-label-primary);
}

.workspaceLabel {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chevron {
  flex: none;
  color: var(--dsw-alias-label-caption);
}
</style>
