<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { IconCheck, ICON_STROKE } from './icons';
import { isMenuItem, type MenuEntry } from './menu.types';

/**
 * dsh Menu primitive. The default slot is the anchor; when `open`, the list is
 * teleported to <body>, fixed-positioned against the anchor (4px gap, 12px
 * viewport margin, flips when it does not fit) and closes on outside
 * pointerdown / Escape.
 */
const props = withDefaults(
  defineProps<{
    open: boolean;
    items: MenuEntry[];
    footer?: MenuEntry[];
    selectedId?: string | null;
    selectedIds?: string[];
    align?: 'start' | 'end';
    side?: 'bottom' | 'top' | 'right';
    dense?: boolean;
    compact?: boolean;
    selection?: 'check' | 'fill';
    minWidth?: number;
    ariaLabel?: string;
  }>(),
  {
    footer: () => [],
    selectedId: null,
    selectedIds: () => [],
    align: 'start',
    side: 'bottom',
    dense: false,
    compact: false,
    selection: 'check',
    minWidth: undefined,
    ariaLabel: undefined,
  },
);

const emit = defineEmits<{ select: [id: string]; close: [] }>();

const rootRef = ref<HTMLElement | null>(null);
const listRef = ref<HTMLElement | null>(null);
const style = ref<Record<string, string>>({ visibility: 'hidden' });

const GAP = 4;
const MARGIN = 12;

function isSelected(id: string) {
  return props.selectedId === id || props.selectedIds.includes(id);
}

function position() {
  const anchor = rootRef.value;
  const list = listRef.value;
  if (!anchor || !list) return;
  const r = anchor.getBoundingClientRect();
  const w = list.offsetWidth;
  const h = list.offsetHeight;
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  let x: number;
  let y: number;
  if (props.side === 'right') {
    x = r.right + GAP;
    y = r.top;
    if (x + w > vw - MARGIN) x = r.left - GAP - w;
  } else {
    x = props.align === 'end' ? r.right - w : r.left;
    const below = r.bottom + GAP;
    const above = r.top - GAP - h;
    if (props.side === 'top') {
      y = above >= MARGIN ? above : below;
    } else {
      y = below + h <= vh - MARGIN ? below : above >= MARGIN ? above : below;
    }
  }
  x = Math.max(MARGIN, Math.min(x, vw - MARGIN - w));
  y = Math.max(MARGIN, Math.min(y, vh - MARGIN - h));
  style.value = { left: `${x}px`, top: `${y}px`, visibility: 'visible' };
}

function onPointerDown(event: PointerEvent) {
  const target = event.target as Node | null;
  if (!target) return;
  if (rootRef.value?.contains(target) || listRef.value?.contains(target)) return;
  emit('close');
}

function onKeyDown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.stopPropagation();
    emit('close');
    return;
  }
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    const buttons = Array.from(
      listRef.value?.querySelectorAll<HTMLButtonElement>('button[role="menuitem"]:not(:disabled)') ?? [],
    );
    if (buttons.length === 0) return;
    event.preventDefault();
    const index = buttons.findIndex((b) => b === document.activeElement);
    const next =
      event.key === 'ArrowDown'
        ? buttons[(index + 1) % buttons.length]
        : buttons[(index - 1 + buttons.length) % buttons.length];
    next?.focus();
  }
}

function attach() {
  document.addEventListener('pointerdown', onPointerDown, true);
  document.addEventListener('keydown', onKeyDown, true);
  window.addEventListener('scroll', position, true);
  window.addEventListener('resize', position);
}

function detach() {
  document.removeEventListener('pointerdown', onPointerDown, true);
  document.removeEventListener('keydown', onKeyDown, true);
  window.removeEventListener('scroll', position, true);
  window.removeEventListener('resize', position);
}

watch(
  () => props.open,
  async (open) => {
    if (open) {
      style.value = { visibility: 'hidden' };
      await nextTick();
      position();
      attach();
    } else {
      detach();
    }
  },
  { immediate: true },
);

onBeforeUnmount(detach);

function onSelect(id: string) {
  emit('select', id);
}
</script>

<template>
  <span ref="rootRef" class="menuAnchor">
    <slot />
    <Teleport to="body">
      <div
        v-if="open"
        ref="listRef"
        :class="['list', { denseList: dense, compactList: compact }]"
        :style="[style, minWidth ? { minWidth: `${minWidth}px` } : null]"
        role="menu"
        :aria-label="ariaLabel"
      >
        <div class="viewport" role="presentation">
          <template v-for="(entry, index) in items" :key="index">
            <div v-if="'type' in entry && entry.type === 'separator'" class="separator" role="separator" />
            <div v-else-if="'type' in entry && entry.type === 'label'" class="label" role="presentation">{{ entry.text }}</div>
            <div v-else-if="isMenuItem(entry)" class="itemWrap">
              <button
                type="button"
                role="menuitem"
                :class="['item', { selectedFill: selection === 'fill' && isSelected(entry.id), danger: entry.danger }]"
                :disabled="entry.disabled"
                @click="onSelect(entry.id)"
              >
                <span v-if="entry.icon" class="itemIcon">
                  <component :is="entry.icon" :size="compact ? 14 : 16" :stroke-width="ICON_STROKE" />
                </span>
                <span class="itemLabel">{{ entry.label }}</span>
                <span v-if="entry.hint" class="itemHint">{{ entry.hint }}</span>
                <IconCheck
                  v-if="selection === 'check' && isSelected(entry.id)"
                  class="check"
                  :size="16"
                  :stroke-width="ICON_STROKE"
                />
              </button>
            </div>
          </template>
        </div>
        <div v-if="footer.length" class="footer" role="presentation">
          <template v-for="(entry, index) in footer" :key="`f${index}`">
            <div v-if="'type' in entry && entry.type === 'separator'" class="separator" role="separator" />
            <div v-else-if="'type' in entry && entry.type === 'label'" class="label" role="presentation">{{ entry.text }}</div>
            <div v-else-if="isMenuItem(entry)" class="itemWrap">
              <button
                type="button"
                role="menuitem"
                :class="['item', { danger: entry.danger }]"
                :disabled="entry.disabled"
                @click="onSelect(entry.id)"
              >
                <span v-if="entry.icon" class="itemIcon">
                  <component :is="entry.icon" :size="compact ? 14 : 16" :stroke-width="ICON_STROKE" />
                </span>
                <span class="itemLabel">{{ entry.label }}</span>
              </button>
            </div>
          </template>
        </div>
      </div>
    </Teleport>
  </span>
</template>

<style scoped>
.menuAnchor {
  position: relative;
  display: inline-flex;
  min-width: 0;
}

.list {
  position: fixed;
  z-index: 1100;
  box-sizing: border-box;
  padding: 4px;
  display: flex;
  flex-direction: column;
  gap: 0;
  min-width: 218px;
  max-width: 360px;
  max-height: calc(100vh - 24px);
  border: 0;
  border-radius: 20px;
  background: var(--dsw-specific-menu);
  --dsw-elevation-stroke-color: var(--dsw-alias-border-l1);
  box-shadow: var(--dsw-elevation-prominent);
  --dsh-scrollbar-thumb: var(--dsw-alias-scrollbar-bg-l2);
  --dsh-scrollbar-thumb-hover: var(--dsw-alias-scrollbar-hover-l2);
}

.viewport {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow-y: auto;
}

.footer {
  flex: none;
  display: flex;
  flex-direction: column;
  margin-top: 4px;
  padding-top: 4px;
  border-top: 0.5px solid var(--dsw-alias-border-l2);
}

.itemWrap {
  position: relative;
}

.item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 40px;
  padding: 8px 10px;
  border: none;
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  line-height: 22px;
  color: var(--dsw-alias-label-primary);
  text-align: left;
}

.item:hover:not(:disabled),
.item:focus-visible {
  background: var(--dsw-alias-interactive-bg-hover);
  outline: none;
}

.denseList .item {
  min-height: 34px;
  padding-block: 5px;
}

.denseList .label {
  padding-block: 4px;
}

.list.compactList {
  min-width: 164px;
  padding: 2px;
  border-radius: 7px;
}

.compactList .item {
  min-height: 26px;
  gap: 6px;
  padding: 3px 7px;
  border-radius: 5px;
  font-size: 12px;
  line-height: 18px;
}

.compactList .itemIcon {
  width: 14px;
  height: 14px;
}

.compactList .separator {
  margin: 2px;
}

.compactList .label {
  padding: 4px 7px;
  font-size: 11px;
  line-height: 16px;
}

.item:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.itemIcon {
  display: inline-flex;
  flex: none;
  width: 16px;
  height: 16px;
  align-items: center;
  justify-content: center;
  color: var(--dsw-alias-label-tertiary);
}

.itemLabel {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.itemHint {
  flex: none;
  max-width: 45%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  line-height: 18px;
  color: var(--dsw-alias-label-tertiary);
}

.check {
  flex: none;
  color: var(--dsw-alias-label-primary);
}

.selectedFill {
  background: var(--dsw-alias-interactive-bg-hover);
}

.danger {
  color: var(--dsw-alias-state-error-primary);
}

.danger .itemIcon {
  color: var(--dsw-alias-state-error-primary);
}

.danger:hover:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-hover-danger);
}

.label {
  padding: 8px 10px;
  font-size: 12px;
  line-height: 16px;
  color: var(--dsw-alias-label-tertiary);
}

.separator {
  height: 0.5px;
  margin: 4px 2px;
  background: var(--dsw-alias-border-l1);
}
</style>
