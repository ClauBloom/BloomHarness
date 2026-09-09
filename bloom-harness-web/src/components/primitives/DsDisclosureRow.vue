<script setup lang="ts">
import { IconChevronDown, ICON_STROKE } from './icons';

/**
 * dsh DisclosureRow: 24px row with a 16px leading slot (icon at rest, chevron
 * on hover), a 13px title, optional collapsed content and an expandable body.
 */
const props = withDefaults(
  defineProps<{
    open: boolean;
    expandable?: boolean;
    title: string;
    /** Keep the collapsed content (summary) visible while open. */
    keepContentWhenOpen?: boolean;
    rowClass?: string;
  }>(),
  { expandable: true, keepContentWhenOpen: false, rowClass: undefined },
);

const emit = defineEmits<{ toggle: [] }>();

function onToggle() {
  if (props.expandable) emit('toggle');
}

function onKey(event: KeyboardEvent) {
  if (!props.expandable) return;
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault();
    emit('toggle');
  }
}
</script>

<template>
  <div class="disclosure" :data-open="open || undefined">
    <div
      :class="['row', rowClass]"
      :data-expandable="expandable || undefined"
      :role="expandable ? 'button' : undefined"
      :tabindex="expandable ? 0 : undefined"
      :aria-expanded="expandable ? open : undefined"
      @click="onToggle"
      @keydown="onKey"
    >
      <span class="leading">
        <template v-if="open && expandable">
          <IconChevronDown class="chevron" :size="14" :stroke-width="ICON_STROKE" />
        </template>
        <template v-else-if="expandable">
          <span class="iconIdle"><slot name="icon" /></span>
          <IconChevronDown class="chevron chevronHover" :size="14" :stroke-width="ICON_STROKE" />
        </template>
        <template v-else>
          <slot name="icon" />
        </template>
      </span>
      <span class="title">{{ title }}</span>
      <template v-if="!open || keepContentWhenOpen">
        <slot name="summary" />
      </template>
      <slot name="trailing" />
    </div>
    <div v-if="open" class="body">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.disclosure {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-width: 0;
}

.row {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  height: calc(24px + var(--dsh-content-font-delta, 0px));
  min-width: 0;
}

.row[data-expandable] {
  cursor: pointer;
}

.row:focus-visible {
  outline: none;
  border-radius: 6px;
  box-shadow: 0 0 0 2px var(--dsw-alias-border-l3);
}

.leading {
  position: relative;
  flex: none;
  width: calc(16px + var(--dsh-content-font-delta, 0px));
  height: calc(16px + var(--dsh-content-font-delta, 0px));
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-right: 6px;
  padding: 0;
  border: none;
  background: none;
  color: var(--dsw-alias-label-tertiary);
}

.leading :deep(svg:not([data-state])) {
  width: calc(14px + var(--dsh-content-font-delta, 0px));
  height: calc(14px + var(--dsh-content-font-delta, 0px));
}

.iconIdle {
  display: inline-flex;
  opacity: 1;
  transition: opacity 100ms ease;
}

.chevron {
  color: var(--dsw-alias-label-secondary);
}

.chevronHover {
  position: absolute;
  inset: 0;
  margin: auto;
  opacity: 0;
  transition: opacity 100ms ease;
}

.row:hover .iconIdle {
  opacity: 0;
}

.row:hover .chevronHover {
  opacity: 1;
}

.title {
  flex: none;
  font-size: var(--dsh-content-font-size-secondary, 13px);
  line-height: calc(24px + var(--dsh-content-font-delta, 0px));
  color: var(--dsw-alias-label-secondary);
}

.body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
</style>
