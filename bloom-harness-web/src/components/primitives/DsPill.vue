<script setup lang="ts">
/** dsh Pill: 24px capsule; interactive when a click handler is attached. */
withDefaults(defineProps<{ active?: boolean; interactive?: boolean; tone?: 'default' | 'error' }>(), {
  active: false,
  interactive: false,
  tone: 'default',
});
</script>

<template>
  <component
    :is="interactive ? 'button' : 'span'"
    :type="interactive ? 'button' : undefined"
    :class="['pill', { interactive, active, error: tone === 'error' }]"
  >
    <slot />
  </component>
</template>

<style scoped>
.pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 8px;
  border: none;
  border-radius: 12px;
  font-size: 12px;
  line-height: 18px;
  color: var(--dsw-alias-label-secondary);
  background: var(--dsw-alias-bg-layer-2);
  white-space: nowrap;
}

.interactive {
  cursor: pointer;
}

.interactive:hover {
  background: var(--dsw-alias-interactive-bg-hover);
}

.active {
  color: var(--dsw-alias-label-primary);
  background: var(--dsw-alias-button-ghost-active-fill);
  box-shadow: inset 0 0 0 1px var(--dsw-alias-button-ghost-active-border);
}

.error {
  color: var(--dsw-alias-state-error-primary);
}
</style>
