<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { useUiStore } from '@/stores/uiStore';
import { IconClose, IconData, IconSettings, ICON_STROKE } from '@/components/primitives/icons';
import GeneralSection from './GeneralSection.vue';
import ModelsSection from './ModelsSection.vue';

/**
 * dsh SettingsRoot panel: fixed overlay with a blurred mask and a centred
 * 800 × min(800px, 100vh − 48px) card — 188px nav rail (title + section cells)
 * and a content column with a 54px header (close) and the scrolling options.
 */
const ui = useUiStore();
const closeRef = ref<HTMLButtonElement | null>(null);
let restoreFocus: HTMLElement | null = null;

const sections = [
  { id: 'general', label: '通用设置', icon: IconSettings },
  { id: 'models', label: '模型', icon: IconData },
] as const;

function onKeyDown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.stopPropagation();
    ui.closeSettings();
  }
}

watch(
  () => ui.settingsOpen,
  async (open) => {
    if (open) {
      restoreFocus = document.activeElement as HTMLElement | null;
      document.addEventListener('keydown', onKeyDown);
      await nextTick();
      closeRef.value?.focus();
    } else {
      document.removeEventListener('keydown', onKeyDown);
      restoreFocus?.focus?.();
      restoreFocus = null;
    }
  },
  { immediate: true },
);

onBeforeUnmount(() => document.removeEventListener('keydown', onKeyDown));
</script>

<template>
  <Teleport to="body">
    <div v-if="ui.settingsOpen" class="overlay" role="presentation">
      <div class="mask" aria-hidden="true" @click="ui.closeSettings()" />
      <div class="panel" role="dialog" aria-modal="true" aria-labelledby="bloom-settings-title">
        <nav class="nav">
          <div id="bloom-settings-title" class="navTitle">设置</div>
          <div class="navList">
            <button
              v-for="section in sections"
              :key="section.id"
              type="button"
              :class="['navCell', { active: ui.settingsSection === section.id }]"
              :aria-current="ui.settingsSection === section.id ? 'true' : undefined"
              @click="ui.settingsSection = section.id"
            >
              <component :is="section.icon" class="navIcon" :size="16" :stroke-width="ICON_STROKE" />
              <span class="navLabel">{{ section.label }}</span>
            </button>
          </div>
        </nav>
        <div class="content">
          <div class="header">
            <div class="actions" />
            <button ref="closeRef" type="button" class="close" aria-label="关闭" @click="ui.closeSettings()">
              <IconClose :size="14" :stroke-width="ICON_STROKE" />
            </button>
          </div>
          <div class="options">
            <GeneralSection v-if="ui.settingsSection === 'general'" />
            <ModelsSection v-else-if="ui.settingsSection === 'models'" />
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.mask {
  position: absolute;
  inset: 0;
  background: var(--dsw-alias-bg-mask-1);
  backdrop-filter: var(--dsw-mask-blur);
}

.panel {
  position: relative;
  z-index: 1;
  display: flex;
  width: 800px;
  height: min(800px, calc(100vh - 48px));
  max-width: calc(100vw - 48px);
  border-radius: 32px;
  overflow: hidden;
  background: var(--dsw-alias-bg-layer-2);
  box-shadow: var(--dsw-elevation-prominent);
  --dsh-scrollbar-thumb: var(--dsw-alias-scrollbar-bg-l2);
  --dsh-scrollbar-thumb-hover: var(--dsw-alias-scrollbar-hover-l2);
}

.nav {
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 18px;
  width: 188px;
  padding: 22px 12px 0;
  box-sizing: border-box;
}

.navTitle {
  padding: 0 12px;
  font-size: 16px;
  line-height: 24px;
  font-weight: 500;
  color: var(--dsw-alias-label-primary);
}

.navList {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.navCell {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 9px 16px 9px 12px;
  box-sizing: border-box;
  border: none;
  border-radius: 12px;
  background: transparent;
  cursor: pointer;
  font-family: inherit;
  font-size: 14px;
  line-height: 22px;
  font-weight: 400;
  color: var(--dsw-alias-label-primary);
  text-align: left;
}

.navCell:hover {
  background: var(--dsw-specific-sidebar-nav-item-hover);
}

.navCell.active {
  background: var(--dsw-specific-sidebar-nav-item-active);
}

.navIcon {
  flex: none;
}

.navLabel {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.header {
  flex: none;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  height: 54px;
  padding: 20px 14px 8px 10px;
  box-sizing: border-box;
}

.actions {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-left: auto;
}

.close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  border-radius: 28px;
  background: transparent;
  cursor: pointer;
  color: var(--dsw-alias-label-primary);
}

.close:hover {
  background: var(--dsw-alias-interactive-bg-hover);
}

.options {
  flex: 1;
  min-height: 0;
  padding: 0 24px 24px;
  overflow-y: auto;
}
</style>
