<script setup lang="ts">
import type { ConnectionState } from '@/stores/uiStore';
import { IconCheck, IconWarning, ICON_STROKE } from './icons';

/** dsh ConnectionIndicator: 32px pill; hidden when `state` is undefined. */
defineProps<{ state: ConnectionState }>();
const emit = defineEmits<{ reconnect: [] }>();

const LABELS = {
  disconnected: '连接异常',
  reconnect: '立即重连',
  connecting: '自动重连中',
  connected: '连接成功',
  reconnectAction: '连接异常，点击立即重连',
  restartAction: '连接中断，正在自动重试，点击立即重连',
};
</script>

<template>
  <div v-if="state === 'recovered'" class="indicator success" role="status" :aria-label="LABELS.connected">
    <span class="icon" aria-hidden="true"><IconCheck :size="14" :stroke-width="ICON_STROKE" /></span>
    <span class="label">
      <span class="sizeLabel" aria-hidden="true">{{ LABELS.disconnected }}</span>
      <span class="sizeLabel" aria-hidden="true">{{ LABELS.reconnect }}</span>
      <span class="sizeLabel" aria-hidden="true">{{ LABELS.connecting }}<span class="dots">...</span></span>
      <span class="sizeLabel" aria-hidden="true">{{ LABELS.connected }}</span>
      <span class="stateLabel">{{ LABELS.connected }}</span>
    </span>
  </div>
  <button
    v-else-if="state"
    type="button"
    class="indicator warning"
    :data-phase="state"
    :aria-label="state === 'connecting' ? LABELS.restartAction : LABELS.reconnectAction"
    @click="emit('reconnect')"
  >
    <span class="icon" aria-hidden="true"><IconWarning :size="14" :stroke-width="ICON_STROKE" /></span>
    <span class="label">
      <span class="sizeLabel" aria-hidden="true">{{ LABELS.disconnected }}</span>
      <span class="sizeLabel" aria-hidden="true">{{ LABELS.reconnect }}</span>
      <span class="sizeLabel" aria-hidden="true">{{ LABELS.connecting }}<span class="dots">...</span></span>
      <span class="sizeLabel" aria-hidden="true">{{ LABELS.connected }}</span>
      <span class="stateLabel">
        <template v-if="state === 'connecting'">
          {{ LABELS.connecting }}<span class="dots" aria-hidden="true"><span>.</span><span class="secondDot">.</span><span class="thirdDot">.</span></span>
        </template>
        <template v-else>{{ LABELS.disconnected }}</template>
      </span>
      <span class="hoverLabel">{{ LABELS.reconnect }}</span>
    </span>
  </button>
</template>

<style scoped>
.indicator {
  flex: none;
  display: inline-grid;
  grid-template-columns: 14px max-content;
  align-items: center;
  column-gap: 4px;
  height: 32px;
  padding: 0 10px;
  box-sizing: border-box;
  border: none;
  border-radius: 8px;
  font-family: inherit;
  font-size: 12px;
  font-weight: 500;
  line-height: 18px;
  white-space: nowrap;
  transition:
    background-color 160ms ease-out,
    color 160ms ease-out;
}

.warning {
  background: var(--dsw-alias-state-warn-tertiary);
  color: var(--dsw-alias-state-warn-label);
  cursor: pointer;
}

.warning:active {
  background: color-mix(in srgb, var(--dsw-alias-state-warn-tertiary), var(--dsw-alias-state-warn-primary) 10%);
}

.warning:focus-visible {
  outline: 2px solid var(--dsw-alias-state-warn-label);
  outline-offset: 2px;
}

.success {
  background: var(--dsw-alias-state-success-tertiary);
  color: var(--dsw-alias-state-success-primary);
}

.icon {
  display: grid;
  place-items: center;
  width: 14px;
  height: 14px;
}

.label {
  display: grid;
  text-align: left;
}

.stateLabel,
.hoverLabel,
.sizeLabel {
  grid-area: 1 / 1;
}

.sizeLabel {
  visibility: hidden;
}

.warning:is(:hover, :focus-visible) .stateLabel {
  visibility: hidden;
}

.hoverLabel {
  visibility: hidden;
}

.warning:is(:hover, :focus-visible) .hoverLabel {
  visibility: visible;
}

.dots {
  display: inline-block;
  width: 1.5em;
  text-align: left;
}

.secondDot {
  animation: reveal-second-dot 1.5s step-end infinite;
}

.thirdDot {
  animation: reveal-third-dot 1.5s step-end infinite;
}

@keyframes reveal-second-dot {
  0%,
  33.32% {
    visibility: hidden;
  }
  33.33%,
  100% {
    visibility: visible;
  }
}

@keyframes reveal-third-dot {
  0%,
  66.65% {
    visibility: hidden;
  }
  66.66%,
  100% {
    visibility: visible;
  }
}

@media (prefers-reduced-motion: reduce) {
  .secondDot,
  .thirdDot {
    animation: none;
  }
}
</style>
