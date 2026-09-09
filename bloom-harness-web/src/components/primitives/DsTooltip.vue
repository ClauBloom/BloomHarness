<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';

/**
 * dsh Tooltip: wraps a single child (display: contents) and shows a fixed
 * bubble beside it after `delay` ms of hover (focus is immediate).
 */
const props = withDefaults(
  defineProps<{
    label: string;
    side?: 'right' | 'bottom' | 'top';
    delay?: number;
    disabled?: boolean;
  }>(),
  { side: 'right', delay: 300, disabled: false },
);

const wrapRef = ref<HTMLElement | null>(null);
const visible = ref(false);
const pos = ref({ x: 0, y: 0 });
let timer: ReturnType<typeof setTimeout> | null = null;
let anchor: HTMLElement | null = null;

const MARGIN = 12;

function place() {
  if (!anchor) return;
  const r = anchor.getBoundingClientRect();
  if (props.side === 'right') {
    pos.value = { x: r.right + 10, y: r.top + r.height / 2 };
  } else if (props.side === 'bottom') {
    pos.value = { x: Math.min(Math.max(r.left + r.width / 2, MARGIN), window.innerWidth - MARGIN), y: r.bottom + 8 };
  } else {
    pos.value = { x: Math.min(Math.max(r.left + r.width / 2, MARGIN), window.innerWidth - MARGIN), y: r.top - 8 };
  }
}

function show(immediate: boolean) {
  if (props.disabled) return;
  clear();
  const run = () => {
    place();
    visible.value = true;
  };
  if (immediate || props.delay <= 0) run();
  else timer = setTimeout(run, props.delay);
}

function clear() {
  if (timer) {
    clearTimeout(timer);
    timer = null;
  }
}

function hide() {
  clear();
  visible.value = false;
}

const onEnter = () => show(false);
const onLeave = () => hide();
const onFocus = () => show(true);
const onBlur = () => hide();
const onDown = () => hide();

onMounted(() => {
  anchor = (wrapRef.value?.firstElementChild as HTMLElement | null) ?? null;
  if (!anchor) return;
  anchor.addEventListener('mouseenter', onEnter);
  anchor.addEventListener('mouseleave', onLeave);
  anchor.addEventListener('focus', onFocus);
  anchor.addEventListener('blur', onBlur);
  anchor.addEventListener('pointerdown', onDown);
});

onBeforeUnmount(() => {
  clear();
  if (!anchor) return;
  anchor.removeEventListener('mouseenter', onEnter);
  anchor.removeEventListener('mouseleave', onLeave);
  anchor.removeEventListener('focus', onFocus);
  anchor.removeEventListener('blur', onBlur);
  anchor.removeEventListener('pointerdown', onDown);
});
</script>

<template>
  <span ref="wrapRef" class="wrap">
    <slot />
  </span>
  <Teleport to="body">
    <span
      v-if="visible && !disabled"
      class="bubble"
      role="tooltip"
      :data-side="side"
      :style="{ left: `${pos.x}px`, top: `${pos.y}px` }"
    >{{ label }}</span>
  </Teleport>
</template>

<style scoped>
.wrap {
  display: contents;
}

.bubble {
  position: fixed;
  z-index: 1200;
  width: max-content;
  max-width: 50vw;
  padding: 3px 7px;
  border-radius: 8px;
  background: var(--dsw-alias-tooltip-bg);
  color: var(--dsw-static-neutral-bluish-00);
  font-size: 13px;
  line-height: 20px;
  white-space: pre-line;
  overflow-wrap: break-word;
  pointer-events: none;
  animation: tooltip-in 150ms var(--ds-ease-in-out);
}

.bubble[data-side='right'] {
  transform: translateY(-50%);
}

.bubble[data-side='bottom'] {
  transform: translateX(-50%);
}

.bubble[data-side='top'] {
  transform: translate(-50%, -100%);
}

@keyframes tooltip-in {
  from {
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .bubble {
    animation: none;
  }
}
</style>
