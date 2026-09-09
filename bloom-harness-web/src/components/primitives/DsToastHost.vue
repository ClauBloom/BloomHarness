<script setup lang="ts">
import { useUiStore } from '@/stores/uiStore';
import { IconWarning, ICON_STROKE } from './icons';

/**
 * dsh Toast host: fixed contrast pills at top 120px that hold 3s then fade 1s.
 * Items come from the ui store; each removes itself when its fade finishes.
 */
const ui = useUiStore();
const HOLD_MS = 3000;
</script>

<template>
  <Teleport to="body">
    <div
      v-for="(toast, index) in ui.toasts"
      :key="toast.id"
      class="toast"
      role="alert"
      :style="{ top: `${120 + index * 56}px`, '--dsh-toast-hold': `${HOLD_MS}ms` }"
      @animationend="(e) => e.animationName.includes('fade') && ui.dismissToast(toast.id)"
    >
      <span v-if="toast.tone === 'warning'" class="icon" aria-hidden="true">
        <IconWarning :size="16" :stroke-width="ICON_STROKE" />
      </span>
      <span class="text">{{ toast.text }}</span>
    </div>
  </Teleport>
</template>

<style scoped>
.toast {
  position: fixed;
  left: 50%;
  z-index: 1100;
  pointer-events: none;
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: min(560px, calc(100vw - 48px));
  padding: 12px 16px;
  border-radius: 14px;
  background: var(--dsw-alias-toast-bg, var(--dsw-alias-button-contrast-fill));
  color: var(--dsw-static-neutral-bluish-00, #ffffff);
  font-size: 14px;
  line-height: 22px;
  box-shadow: var(--dsw-shadow-lv3);
  transform: translateX(-50%);
  animation:
    dsh-toast-in 160ms ease-out,
    dsh-toast-fade 1000ms ease var(--dsh-toast-hold, 3000ms) forwards;
}

.icon {
  display: grid;
  place-items: center;
  flex: none;
  color: var(--dsw-alias-state-warn-label);
}

.text {
  min-width: 0;
  overflow-wrap: anywhere;
}

@keyframes dsh-toast-in {
  from {
    opacity: 0;
    transform: translate(-50%, -6px);
  }
  to {
    opacity: 1;
    transform: translate(-50%, 0);
  }
}

@keyframes dsh-toast-fade {
  to {
    opacity: 0;
  }
}
</style>
