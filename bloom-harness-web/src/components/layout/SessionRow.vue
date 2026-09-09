<script setup lang="ts">
import { computed, ref } from 'vue';
import type { SessionMetadata } from '@/types/session.types';
import DsMenu from '@/components/primitives/DsMenu.vue';
import DsStateDot from '@/components/primitives/DsStateDot.vue';
import type { MenuEntry } from '@/components/primitives/menu.types';
import { IconArchive, IconEdit, IconEllipsis, IconUnarchive, ICON_STROKE } from '@/components/primitives/icons';
import { useSessionTitles } from '@/composables/useSessionTitles';
import { relativeTime } from '@/utils/format';

/**
 * dsh SessionNodeItem: 32px row — 16px status slot (dot only while running),
 * ellipsized title, trailing relative time that yields to the … menu on hover.
 * `blank` renders the current unsent "New session" (no time, no menu).
 * `archived` rows are the browser-local archive (dimmed, offer restore).
 */
const props = withDefaults(
  defineProps<{
    session: SessionMetadata;
    selected: boolean;
    running: boolean;
    /** Flat list mode without a workspace header. */
    flat?: boolean;
    now: number;
    blank?: boolean;
    archived?: boolean;
    dropMarker?: 'before' | 'after' | null;
    draggable?: boolean;
  }>(),
  { flat: false, blank: false, archived: false, dropMarker: null, draggable: false },
);

const emit = defineEmits<{
  open: [id: string];
  rename: [id: string];
  archive: [id: string];
  unarchive: [id: string];
  dragstart: [event: DragEvent];
  dragover: [event: DragEvent];
  drop: [event: DragEvent];
  dragend: [event: DragEvent];
}>();

const { sessionTitle } = useSessionTitles();
const title = computed(() => (props.blank ? '新会话' : sessionTitle(props.session)));
const time = computed(() => (props.blank ? '' : relativeTime(props.session.updatedAt ?? props.session.createdAt, props.now)));

const menuOpen = ref(false);
const isDragging = ref(false);

function handleDragStart(event: DragEvent) {
  isDragging.value = true;
  emit('dragstart', event);
}

function handleDragEnd(event: DragEvent) {
  isDragging.value = false;
  emit('dragend', event);
}

const menuItems = computed<MenuEntry[]>(() =>
  props.archived
    ? [
        { id: 'rename', label: '重命名', icon: IconEdit },
        { id: 'unarchive', label: '取消归档', icon: IconUnarchive },
      ]
    : [
        { id: 'rename', label: '重命名', icon: IconEdit },
        { id: 'archive', label: '归档会话', icon: IconArchive },
      ],
);

function onMenuSelect(id: string) {
  menuOpen.value = false;
  if (id === 'rename') emit('rename', props.session.id);
  else if (id === 'archive') emit('archive', props.session.id);
  else if (id === 'unarchive') emit('unarchive', props.session.id);
}
</script>

<template>
  <div
    :class="[
      'sessionRow',
      {
        selected,
        menuOpen,
        archived,
        isDragging,
        flatSessionRowWithoutStatus: flat && !running,
        dropBefore: dropMarker === 'before',
        dropAfter: dropMarker === 'after',
      },
    ]"
    role="treeitem"
    :aria-selected="selected"
    tabindex="0"
    :draggable="draggable && !blank ? 'true' : undefined"
    @click="emit('open', session.id)"
    @keydown.enter.prevent="emit('open', session.id)"
    @dragstart="handleDragStart"
    @dragover="emit('dragover', $event)"
    @drop="emit('drop', $event)"
    @dragend="handleDragEnd"
  >
    <span v-if="!flat || running" class="slot">
      <DsStateDot v-if="running" state="ongoing" />
      <span v-if="running" class="visually-hidden">进行中</span>
    </span>
    <span class="title">{{ title }}</span>
    <span v-if="time" class="time">{{ time }}</span>
    <span v-if="!blank" class="rowActions">
      <DsMenu :open="menuOpen" :items="menuItems" dense align="end" :aria-label="`「${title}」的会话操作`" @select="onMenuSelect" @close="menuOpen = false">
        <button
          type="button"
          class="iconButton"
          :aria-label="`「${title}」的会话操作`"
          aria-haspopup="menu"
          :aria-expanded="menuOpen"
          @click.stop="menuOpen = !menuOpen"
        >
          <IconEllipsis :size="16" :stroke-width="ICON_STROKE" />
        </button>
      </DsMenu>
    </span>
  </div>
</template>

<style scoped>
.sessionRow {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0;
  height: 32px;
  border-radius: 8px;
  padding: 0 8px;
  box-sizing: border-box;
  cursor: pointer;
  user-select: none;
  color: var(--dsw-alias-label-primary);
  outline: none;
  animation: row-in 150ms var(--ds-ease-in-out);
}

.sessionRow:hover,
.sessionRow:focus-visible,
.sessionRow.selected,
.sessionRow.menuOpen {
  background: var(--dsw-alias-interactive-bg-hover);
}

.sessionRow.isDragging {
  opacity: 0.35;
  background: var(--dsw-alias-interactive-bg-hover);
}

.sessionRow.archived {
  color: var(--dsw-alias-label-tertiary);
}

@keyframes row-in {
  from {
    opacity: 0;
  }
}

.slot {
  flex: none;
  width: 16px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--dsw-alias-label-tertiary);
}

.title {
  flex: 1;
  min-width: 0;
  margin: 0 6px 0 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  line-height: 20px;
}

.flatSessionRowWithoutStatus .title {
  margin-left: 0;
}

.time {
  flex: none;
  font-size: 12px;
  line-height: 20px;
  color: var(--dsw-alias-label-tertiary);
}

.sessionRow:hover .time,
.sessionRow.menuOpen .time {
  display: none;
}

.rowActions {
  flex: none;
  display: none;
  align-items: center;
  gap: 12px;
  height: 20px;
}

.sessionRow:hover .rowActions,
.sessionRow.menuOpen .rowActions {
  display: inline-flex;
}

.iconButton {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border: none;
  border-radius: 4px;
  padding: 0;
  background: transparent;
  cursor: pointer;
  color: var(--dsw-alias-label-tertiary);
}

.iconButton:hover {
  color: var(--dsw-alias-label-primary);
}

/* Drop markers (dsh Rows.module.css). */
.sessionRow.dropBefore::before,
.sessionRow.dropAfter::after {
  content: '';
  position: absolute;
  z-index: 1;
  left: 0;
  right: 4px;
  height: 12px;
  background:
    linear-gradient(55deg, transparent calc(50% - 1px), var(--dsw-alias-state-business-primary) calc(50% - 1px) calc(50% + 1px), transparent calc(50% + 1px)) 0 0 / 5px 7px no-repeat,
    linear-gradient(125deg, transparent calc(50% - 1px), var(--dsw-alias-state-business-primary) calc(50% - 1px) calc(50% + 1px), transparent calc(50% + 1px)) 0 5px / 5px 7px no-repeat,
    linear-gradient(var(--dsw-alias-state-business-primary) 0 0) 4px 5px / calc(100% - 4px) 2px no-repeat;
  pointer-events: none;
}

.sessionRow.dropBefore::before {
  top: -7px;
}

.sessionRow.dropAfter::after {
  bottom: -7px;
}

@media (prefers-reduced-motion: reduce) {
  .sessionRow {
    animation: none;
  }
}
</style>
