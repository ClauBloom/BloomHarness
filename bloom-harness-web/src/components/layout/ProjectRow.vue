<script setup lang="ts">
import { ref } from 'vue';
import {
  IconEdit,
  IconEllipsis,
  IconFolderClose,
  IconFolderOpen,
  IconPlus,
  IconTrash,
  IconTriangleRight,
  ICON_STROKE,
} from '@/components/primitives/icons';
import DsMenu from '@/components/primitives/DsMenu.vue';
import DsTooltip from '@/components/primitives/DsTooltip.vue';
import type { MenuEntry } from '@/components/primitives/menu.types';

/**
 * dsh ProjectRowItem: the 34px workspace group header. Folder glyph at rest,
 * expand arrow on hover; the trailing actions (… menu, + new session) surface
 * on hover or while the menu is open. Draggable for manual ordering.
 */
withDefaults(
  defineProps<{
    label: string;
    path: string;
    expanded: boolean;
    containsCurrent: boolean;
    /** The "Ungrouped" bucket: no menu, no new-session action, not draggable. */
    ungrouped?: boolean;
    dropMarker?: 'before' | 'after' | null;
    draggable?: boolean;
  }>(),
  { ungrouped: false, dropMarker: null, draggable: false },
);

const emit = defineEmits<{
  toggle: [];
  newSession: [];
  rename: [];
  delete: [];
  dragstart: [event: DragEvent];
  dragover: [event: DragEvent];
  drop: [event: DragEvent];
  dragend: [event: DragEvent];
}>();

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

const menuItems: MenuEntry[] = [
  { id: 'rename', label: '重命名', icon: IconEdit },
  { id: 'delete', label: '删除工作区', icon: IconTrash, danger: true },
];

function onMenuSelect(id: string) {
  menuOpen.value = false;
  if (id === 'rename') emit('rename');
  else if (id === 'delete') emit('delete');
}
</script>

<template>
  <DsTooltip :label="path || '未分组'" :delay="500" :disabled="menuOpen">
    <div
      :class="['projectRow', { menuOpen, isDragging, dropBefore: dropMarker === 'before', dropAfter: dropMarker === 'after' }]"
      role="treeitem"
      :aria-expanded="expanded"
      tabindex="0"
      :draggable="draggable && !ungrouped ? 'true' : undefined"
      @click="emit('toggle')"
      @keydown.enter.prevent="emit('toggle')"
      @keydown.space.prevent="emit('toggle')"
      @dragstart="handleDragStart"
      @dragover="emit('dragover', $event)"
      @drop="emit('drop', $event)"
      @dragend="handleDragEnd"
    >
      <span :class="['slot', 'folder', { folderActive: expanded && containsCurrent }]">
        <IconFolderOpen v-if="expanded" :size="16" :stroke-width="ICON_STROKE" />
        <IconFolderClose v-else :size="16" :stroke-width="ICON_STROKE" />
      </span>
      <span class="slot chevron">
        <IconTriangleRight :class="['arrow', { arrowOpen: expanded }]" :size="12" fill="currentColor" stroke="none" />
      </span>
      <span class="projectText">
        <span class="title">{{ label }}</span>
      </span>
      <span v-if="!ungrouped" class="rowActions">
        <DsMenu :open="menuOpen" :items="menuItems" dense align="end" :aria-label="`「${label}」的工作区操作`" @select="onMenuSelect" @close="menuOpen = false">
          <button
            type="button"
            class="iconButton"
            :aria-label="`「${label}」的工作区操作`"
            aria-haspopup="menu"
            :aria-expanded="menuOpen"
            @click.stop="menuOpen = !menuOpen"
          >
            <IconEllipsis :size="16" :stroke-width="ICON_STROKE" />
          </button>
        </DsMenu>
        <button type="button" class="iconButton" :aria-label="`在「${label}」中新建会话`" @click.stop="emit('newSession')">
          <IconPlus :size="16" :stroke-width="ICON_STROKE" />
        </button>
      </span>
    </div>
  </DsTooltip>
</template>

<style scoped>
.projectRow {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  border-radius: 8px;
  padding: 0 8px;
  box-sizing: border-box;
  cursor: pointer;
  user-select: none;
  color: var(--dsw-alias-label-primary);
  outline: none;
}

.projectRow:hover,
.projectRow:focus-visible,
.projectRow.menuOpen {
  background: var(--dsw-alias-interactive-bg-hover);
}

.projectRow.isDragging {
  opacity: 0.35;
  background: var(--dsw-alias-interactive-bg-hover);
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

.folderActive {
  color: var(--dsw-alias-state-business-primary);
}

.chevron {
  display: none;
  color: var(--dsw-alias-label-caption);
}

.projectRow:hover .chevron,
.projectRow.menuOpen .chevron {
  display: inline-flex;
}

.projectRow:hover .folder,
.projectRow.menuOpen .folder {
  display: none;
}

.arrow {
  transition: transform 150ms var(--ds-ease-in-out);
}

.arrowOpen {
  transform: rotate(90deg);
}

.projectText {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  line-height: 20px;
}

/* Trailing action buttons surface on hover only: bare 16px glyphs, gap 12, tertiary grey. */
.rowActions {
  flex: none;
  display: none;
  align-items: center;
  gap: 12px;
  height: 20px;
}

.projectRow:hover .rowActions,
.projectRow.menuOpen .rowActions {
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

/* Drop markers (dsh Rows.module.css): a small chevron plus a 2px rule in business-primary. */
.projectRow.dropBefore::before,
.projectRow.dropAfter::after {
  content: '';
  position: absolute;
  z-index: 1;
  left: 0;
  right: 0;
  height: 12px;
  background:
    linear-gradient(55deg, transparent calc(50% - 1px), var(--dsw-alias-state-business-primary) calc(50% - 1px) calc(50% + 1px), transparent calc(50% + 1px)) 0 0 / 5px 7px no-repeat,
    linear-gradient(125deg, transparent calc(50% - 1px), var(--dsw-alias-state-business-primary) calc(50% - 1px) calc(50% + 1px), transparent calc(50% + 1px)) 0 5px / 5px 7px no-repeat,
    linear-gradient(var(--dsw-alias-state-business-primary) 0 0) 4px 5px / calc(100% - 4px) 2px no-repeat;
  pointer-events: none;
}

.projectRow.dropBefore::before {
  top: -8px;
}

.projectRow.dropAfter::after {
  bottom: -8px;
}

@media (prefers-reduced-motion: reduce) {
  .arrow {
    transition: none;
  }
}
</style>
