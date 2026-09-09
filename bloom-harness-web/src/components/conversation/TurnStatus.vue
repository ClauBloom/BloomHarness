<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useAgentStore } from '@/stores/agentStore';
import { formatDuration } from '@/utils/format';

/**
 * dsh TurnStatus: shimmering brand-blue status text at the bottom of the flow
 * while the session runs; after 15s a run clock appears to its right.
 */
const store = useAgentStore();
const now = ref(Date.now());
let timer: ReturnType<typeof setInterval> | null = null;

onMounted(() => {
  timer = setInterval(() => (now.value = Date.now()), 1000);
});
onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});

const elapsed = computed(() => (store.turnStartedAt ? now.value - store.turnStartedAt : 0));
const showClock = computed(() => elapsed.value >= 15_000);
</script>

<template>
  <div class="turnStatus" role="status" aria-live="polite">
    正在思考…
    <span v-if="showClock" class="turnStatusClock">{{ formatDuration(elapsed) }}</span>
  </div>
</template>

<style scoped>
.turnStatus {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  height: calc(26px + var(--dsh-content-font-delta, 0px));
  font: var(--dsw-font-s-strong-14);
  font-size: var(--dsh-content-font-size, 14px);
  line-height: calc(22px + var(--dsh-content-font-delta, 0px));
  white-space: nowrap;
  background: linear-gradient(
    90deg,
    var(--dsw-static-deepseek-500) 0%,
    var(--dsw-static-deepseek-500) 40%,
    var(--dsw-static-deepseek-200) 50%,
    var(--dsw-static-deepseek-500) 60%,
    var(--dsw-static-deepseek-500) 100%
  );
  background-position: 100% 0;
  background-size: 250% 100%;
  background-clip: text;
  -webkit-background-clip: text;
  color: transparent;
  -webkit-text-fill-color: transparent;
  animation: dsh-turn-status-shimmer 1.8s linear infinite;
}

@keyframes dsh-turn-status-shimmer {
  to {
    background-position: 0 0;
  }
}

.turnStatusClock {
  margin-left: 8px;
  font: var(--dsw-font-xs-13);
  font-size: var(--dsh-content-font-size-secondary, 13px);
  line-height: calc(20px + var(--dsh-content-font-delta-secondary, 0px));
  font-weight: 400;
  font-variant-numeric: tabular-nums;
  color: var(--dsw-alias-label-caption);
  -webkit-text-fill-color: var(--dsw-alias-label-caption);
}

@media (prefers-reduced-motion: reduce) {
  .turnStatus {
    animation: none;
    background-size: 100% 100%;
  }
}
</style>
