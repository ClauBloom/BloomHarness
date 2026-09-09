<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import {
  SIDEBAR_AUTO_COLLAPSE,
  SIDEBAR_COLLAPSED,
  SIDEBAR_DEFAULT,
  SIDEBAR_MAX,
  SIDEBAR_MIN,
  useUiStore,
} from '@/stores/uiStore';

/**
 * dsh AppFrame: a two-column grid (sidebar | conversation) whose sidebar track
 * collapses to a 56px rail (auto below 1024px) and can be drag-resized.
 */
const ui = useUiStore();
const frameRef = ref<HTMLElement | null>(null);
const viewport = ref(window.innerWidth);
const dragging = ref(false);

const sidebarCollapsed = computed(() => (ui.narrow ? !ui.narrowExpanded : ui.sidebar === 0));

const sidebarWidth = computed(() => {
  if (sidebarCollapsed.value) return SIDEBAR_COLLAPSED;
  const pref = ui.sidebar === 0 ? SIDEBAR_DEFAULT : ui.sidebar;
  return Math.min(SIDEBAR_MAX, Math.max(SIDEBAR_MIN, pref));
});

const gridStyle = computed(() => ({
  gridTemplateColumns: `${sidebarWidth.value}px minmax(0, 1fr)`,
}));

let observer: ResizeObserver | null = null;
let raf = 0;

function measure() {
  const w = frameRef.value?.clientWidth ?? window.innerWidth;
  viewport.value = w;
  ui.setNarrow(w < SIDEBAR_AUTO_COLLAPSE);
}

onMounted(() => {
  measure();
  observer = new ResizeObserver(() => {
    cancelAnimationFrame(raf);
    raf = requestAnimationFrame(measure);
  });
  if (frameRef.value) observer.observe(frameRef.value);
});

onBeforeUnmount(() => {
  observer?.disconnect();
  cancelAnimationFrame(raf);
});

// ---- drag handle (pointer capture, rAF-throttled) ----
let originX = 0;
let baseWidth = 0;
let pendingDx = 0;
let dragRaf = 0;

function onHandleDown(event: PointerEvent) {
  if (sidebarCollapsed.value) return;
  const target = event.currentTarget as HTMLElement;
  target.setPointerCapture(event.pointerId);
  originX = event.clientX;
  baseWidth = sidebarWidth.value;
  dragging.value = true;
}

function onHandleMove(event: PointerEvent) {
  if (!dragging.value) return;
  pendingDx = event.clientX - originX;
  if (!dragRaf) {
    dragRaf = requestAnimationFrame(() => {
      dragRaf = 0;
      ui.setSidebar(baseWidth + pendingDx);
    });
  }
}

function onHandleUp(event: PointerEvent) {
  if (!dragging.value) return;
  const target = event.currentTarget as HTMLElement;
  try {
    target.releasePointerCapture(event.pointerId);
  } catch {
    // capture already released
  }
  dragging.value = false;
}

defineExpose({ viewport });
</script>

<template>
  <div
    ref="frameRef"
    class="frame"
    :style="gridStyle"
    :data-sidebar-collapsed="sidebarCollapsed || undefined"
    :data-dragging="dragging || undefined"
  >
    <div class="sidebarCol">
      <slot name="sidebar" :collapsed="sidebarCollapsed" :width="sidebarWidth" />
    </div>
    <div class="centerCol">
      <slot />
    </div>
    <div
      v-if="!sidebarCollapsed"
      class="handle"
      data-side="sidebar"
      :style="{ left: `${sidebarWidth}px` }"
      @pointerdown="onHandleDown"
      @pointermove="onHandleMove"
      @pointerup="onHandleUp"
      @pointercancel="onHandleUp"
    />
  </div>
</template>

<style scoped>
.frame {
  position: relative;
  display: grid;
  grid-template-rows: 100%;
  height: 100%;
  overflow: hidden;
  background: var(--dsw-alias-bg-base);
  transition: grid-template-columns var(--ds-transition-duration-slow) var(--ds-ease-in-out);
}

.frame[data-dragging] {
  transition: none;
}

@media (prefers-reduced-motion: reduce) {
  .frame {
    transition: none;
  }
}

.sidebarCol {
  min-width: 0;
  overflow: hidden;
  background: var(--dsw-specific-sidebar-fill);
  border-right: 0.5px solid var(--dsw-alias-border-l3);
}

.centerCol {
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.handle {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 8px;
  margin-left: -4px;
  cursor: col-resize;
  z-index: 2;
  touch-action: none;
  transition: left var(--ds-transition-duration-slow) var(--ds-ease-in-out);
}

.frame[data-dragging] .handle {
  transition: none;
}

@media (prefers-reduced-motion: reduce) {
  .handle {
    transition: none;
  }
}
</style>
