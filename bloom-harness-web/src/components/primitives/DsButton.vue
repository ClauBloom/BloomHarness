<script setup lang="ts">
/** dsh Button primitive: primary / ghost / outline, md (36) / sm (28). */
withDefaults(
  defineProps<{
    variant?: 'primary' | 'ghost' | 'outline' | 'danger';
    size?: 'md' | 'sm';
    type?: 'button' | 'submit';
    disabled?: boolean;
    ariaLabel?: string;
  }>(),
  { variant: 'ghost', size: 'md', type: 'button', disabled: false },
);
</script>

<template>
  <button
    :type="type"
    :class="['button', variant, size]"
    :disabled="disabled"
    :aria-label="ariaLabel"
  >
    <span v-if="$slots.icon" class="icon"><slot name="icon" /></span>
    <slot />
  </button>
</template>

<style scoped>
.button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  box-sizing: border-box;
  border: none;
  border-radius: 18px;
  cursor: pointer;
  font-size: 14px;
  line-height: 22px;
  color: var(--dsw-alias-label-primary);
  background: transparent;
  padding: 0 14px;
  white-space: nowrap;
}

.button:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

.md {
  height: 36px;
}

.sm {
  height: 28px;
  font-size: 12px;
  line-height: 18px;
  padding: 0 10px;
  border-radius: 14px;
}

.primary {
  background: var(--dsw-alias-button-primary-fill);
  color: var(--dsw-alias-label-primary-foreground);
}

.primary:hover:not(:disabled) {
  background: var(--dsw-alias-button-primary-hover);
}

.ghost:hover:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-hover);
}

.ghost:active:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-active);
}

.outline {
  border: 0.5px solid var(--dsw-alias-border-l3);
  background: transparent;
}

.outline:hover:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-hover);
}

.danger {
  border: 0.5px solid var(--dsw-alias-state-error-primary);
  color: var(--dsw-alias-state-error-primary);
  background: transparent;
}

.danger:hover:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-hover-danger);
}

.icon {
  display: inline-flex;
  width: 16px;
  height: 16px;
  align-items: center;
  justify-content: center;
}
</style>
