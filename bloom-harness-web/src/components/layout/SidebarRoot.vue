<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useUiStore } from '@/stores/uiStore';
import { useSession } from '@/composables/useSession';
import BrandMark from '@/components/primitives/BrandMark.vue';
import BrandWordmark from '@/components/primitives/BrandWordmark.vue';
import DsTooltip from '@/components/primitives/DsTooltip.vue';
import DsConnectionIndicator from '@/components/primitives/DsConnectionIndicator.vue';
import { IconNewChat, IconPanelLeft, IconSettings, ICON_STROKE } from '@/components/primitives/icons';
import WorkspaceBrowser from './WorkspaceBrowser.vue';

/**
 * dsh SidebarRoot: brand row, New Session, the workspace/session browser and the
 * settings foot. Collapse is a slide + crossfade: content fades in place for
 * 150ms while the AppFrame track slides, then the rail layout applies.
 */
const props = defineProps<{ collapsed: boolean; width: number }>();

const ui = useUiStore();
const { startBlankSession, fetchSessions } = useSession();

const COLLAPSE_SETTLE_MS = 150;

const settled = ref(props.collapsed);
const fading = ref(false);
const railIn = ref(false);
const lastWideWidth = ref(props.width);
let settleTimer: ReturnType<typeof setTimeout> | null = null;

watch(
  () => props.width,
  (w) => {
    if (!props.collapsed) lastWideWidth.value = w;
  },
  { immediate: true },
);

watch(
  () => props.collapsed,
  (collapsed) => {
    if (settleTimer) {
      clearTimeout(settleTimer);
      settleTimer = null;
    }
    if (collapsed) {
      fading.value = true;
      settleTimer = setTimeout(() => {
        settled.value = true;
        fading.value = false;
        railIn.value = true;
        settleTimer = null;
      }, COLLAPSE_SETTLE_MS);
    } else {
      fading.value = false;
      settled.value = false;
      railIn.value = false;
    }
  },
);

onBeforeUnmount(() => {
  if (settleTimer) clearTimeout(settleTimer);
  if (lingerTimer) clearTimeout(lingerTimer);
});

const wide = computed(() => !props.collapsed || !settled.value);
const rootStyle = computed(() => (fading.value ? { width: `${lastWideWidth.value}px` } : undefined));

// Scrollbars inside the column are a pointer affordance (quiet when the pointer is elsewhere).
const quietBars = ref(true);
let lingerTimer: ReturnType<typeof setTimeout> | null = null;
function onPointerEnter() {
  if (lingerTimer) {
    clearTimeout(lingerTimer);
    lingerTimer = null;
  }
  quietBars.value = false;
}
function onPointerLeave() {
  if (lingerTimer) clearTimeout(lingerTimer);
  lingerTimer = setTimeout(() => {
    quietBars.value = true;
    lingerTimer = null;
  }, 2000);
}

function onNewSession() {
  void startBlankSession();
}

function expandSidebar() {
  if (props.collapsed) ui.toggleSidebar();
}

async function reconnect() {
  ui.reportBackendReconnecting();
  await fetchSessions();
}
</script>

<template>
  <div
    :class="['sidebar', { collapsed: collapsed && settled, fading, railIn: railIn && collapsed && settled, quietBars }]"
    :style="rootStyle"
    @pointerenter="onPointerEnter"
    @pointerleave="onPointerLeave"
  >
    <div class="logoRow">
      <template v-if="wide">
        <button type="button" class="brand wide" aria-label="新建会话" @click="onNewSession">
          <span class="brandIdentity" aria-hidden="true">
            <span class="brandMark"><BrandMark :size="24" /></span>
            <span class="brandName"><BrandWordmark /></span>
          </span>
        </button>
        <DsTooltip label="收起侧边栏" :delay="500">
          <button type="button" class="iconButton toggle" aria-label="收起侧边栏" @click="ui.toggleSidebar()">
            <IconPanelLeft class="panelIcon" :size="16" :stroke-width="ICON_STROKE" />
          </button>
        </DsTooltip>
      </template>
      <template v-else>
        <DsTooltip label="打开侧边栏">
          <button type="button" class="iconButton toggle" aria-label="打开侧边栏" @click="ui.toggleSidebar()">
            <span class="railMark" aria-hidden="true"><BrandMark :size="24" /></span>
            <IconPanelLeft class="panelIcon" :size="18" :stroke-width="ICON_STROKE" />
          </button>
        </DsTooltip>
      </template>
    </div>

    <DsTooltip label="新建会话" :disabled="wide">
      <button type="button" class="newSession" aria-label="新建会话" @click="onNewSession">
        <IconNewChat :size="wide ? 14 : 18" :stroke-width="ICON_STROKE" />
        <span v-if="wide" class="newSessionLabel wide">新会话</span>
      </button>
    </DsTooltip>

    <div class="regionArea">
      <WorkspaceBrowser :wide="wide" @expand-sidebar="expandSidebar" />
    </div>

    <div class="footArea">
      <div class="settingsArea">
        <div :class="['triggerRow', { railRow: !wide }]">
          <DsTooltip label="设置" :disabled="wide">
            <button
              type="button"
              :class="['trigger', { rail: !wide }]"
              aria-haspopup="dialog"
              :aria-expanded="ui.settingsOpen"
              aria-label="设置"
              @click="ui.openSettings()"
            >
              <IconSettings :size="wide ? 16 : 18" :stroke-width="ICON_STROKE" />
              <span v-if="wide" class="triggerLabel">设置</span>
            </button>
          </DsTooltip>
          <DsConnectionIndicator v-if="wide" :state="ui.connection" @reconnect="reconnect" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sidebar {
  --dsh-sidebar-inline-padding: 12px;
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 6px var(--dsh-sidebar-inline-padding);
  box-sizing: border-box;
  background: var(--dsw-specific-sidebar-fill);
  color: var(--dsw-alias-label-primary);
  font-size: 14px;
  --dsh-scrollbar-thumb: var(--dsw-alias-scrollbar-bg-l2);
  --dsh-scrollbar-thumb-hover: var(--dsw-alias-scrollbar-hover-l2);
}

/* Rail geometry (dsh): 36×36 control boxes centred in the 56px rail (10px side
   padding), 12px vertical rhythm, 18px from the rail top to the mark's box. */
.sidebar.collapsed {
  padding: 18px 10px 6px;
}

.sidebar.quietBars {
  --dsh-scrollbar-thumb: transparent;
  --dsh-scrollbar-thumb-hover: transparent;
}

.fading > * {
  opacity: 0;
  transition: opacity 150ms var(--ds-ease-in-out);
}

.wide {
  animation: wide-in 200ms var(--ds-ease-in-out);
}

@keyframes wide-in {
  from {
    opacity: 0;
  }
}

.railIn .iconButton,
.railIn .newSession,
.railIn .regionArea {
  animation: rail-in 150ms var(--ds-ease-in-out) backwards;
}

.railIn .footArea {
  animation: rail-fade-in 150ms var(--ds-ease-in-out) backwards;
}

@keyframes rail-in {
  from {
    opacity: 0;
    transform: translateX(49px);
  }
}

@keyframes rail-fade-in {
  from {
    opacity: 0;
  }
}

.logoRow {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  height: 60px;
  padding: 8px 0 8px 4px;
  margin-bottom: 8px;
  box-sizing: border-box;
  overflow: hidden;
}

.collapsed .logoRow {
  height: 36px;
  padding: 0;
  margin-bottom: 12px;
  justify-content: flex-start;
}

.brand {
  flex: 1;
  min-width: 0;
  display: inline-flex;
  align-items: center;
  overflow: hidden;
  padding: 0;
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.brandIdentity {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 24px;
  min-width: 0;
}

.brandMark {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.brandName {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  height: 24px;
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

.collapsed .iconButton {
  width: 36px;
  height: 36px;
  color: var(--dsw-alias-label-primary);
}

.collapsed .toggle .panelIcon {
  display: none;
}

.collapsed .toggle:hover .panelIcon {
  display: inline;
}

.collapsed .toggle:hover .railMark {
  display: none;
}

.railMark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.newSession {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 38px;
  padding: 8px 16px;
  margin: 0 2px 8px;
  box-sizing: border-box;
  border: 0.5px solid var(--dsw-alias-border-l3);
  border-radius: 12px;
  background: var(--dsw-alias-button-elevated-fill);
  color: var(--dsw-alias-label-primary);
  font-size: 14px;
  font-weight: 500;
  line-height: 22px;
  cursor: pointer;
  overflow: hidden;
}

.newSession:hover {
  background: var(--dsw-alias-button-floating-hover);
}

.collapsed .newSession {
  align-self: flex-start;
  width: 36px;
  height: 36px;
  padding: 0;
  margin: 0 0 12px;
  gap: 0;
  border-color: transparent;
  background: transparent;
}

.collapsed .newSession:hover {
  background: var(--dsw-alias-interactive-bg-hover);
}

.newSessionLabel {
  max-width: 200px;
  overflow: hidden;
  white-space: nowrap;
}

.regionArea {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  margin-left: -4px;
  margin-right: calc(-1 * var(--dsh-sidebar-inline-padding));
  padding-left: 4px;
  overflow: hidden;
}

.collapsed .regionArea {
  margin-left: 0;
  margin-right: 0;
  padding-left: 0;
}

.footArea {
  flex: none;
  display: flex;
  flex-direction: column;
}

.settingsArea {
  flex: none;
  min-width: 0;
  width: 100%;
}

.collapsed .footArea {
  align-items: center;
}

.collapsed .settingsArea {
  display: flex;
  justify-content: center;
  width: auto;
}

/* Settings trigger row (dsh SettingsRoot.module.css) */
.triggerRow {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  width: calc(100% + 4px);
  margin: 4px -2px;
}

.triggerRow.railRow {
  width: 36px;
  margin: 8px 0 10px;
}

.trigger {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  width: auto;
  height: 42px;
  margin: 0;
  padding: 0 10px 0 8px;
  box-sizing: border-box;
  border: none;
  border-radius: 12px;
  background: transparent;
  cursor: pointer;
  overflow: hidden;
  color: var(--dsw-alias-label-primary);
  font-family: inherit;
  font-size: 14px;
  line-height: 22px;
}

.trigger:hover {
  background: var(--dsw-alias-interactive-bg-hover);
}

.trigger.rail {
  flex: none;
  width: 36px;
  height: 36px;
  margin: 0;
  justify-content: center;
  gap: 0;
  padding: 0;
  border-radius: 50%;
  corner-shape: round;
}

.triggerLabel {
  overflow: hidden;
  white-space: nowrap;
}

@media (prefers-reduced-motion: reduce) {
  .wide,
  .fading > *,
  .railIn .iconButton,
  .railIn .newSession,
  .railIn .footArea,
  .railIn .regionArea {
    transition: none;
    animation: none;
  }
}
</style>
