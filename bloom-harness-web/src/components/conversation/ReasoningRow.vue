<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import DsDisclosureRow from '@/components/primitives/DsDisclosureRow.vue';
import { IconThink, ICON_STROKE } from '@/components/primitives/icons';

/**
 * dsh ReasoningRow: a 24px "思考" disclosure. Collapsed it shows the first line
 * (or, while streaming, the tail of the latest line, right-anchored); a glare
 * band sweeps the row while running.
 */
const props = defineProps<{ text: string; running: boolean }>();

const open = ref(false);

const summary = computed(() => {
  const lines = props.text.split('\n').map((l) => l.trim()).filter(Boolean);
  if (lines.length === 0) return '';
  return props.running ? lines[lines.length - 1]! : lines[0]!;
});

// Once the block finishes, keep whatever the user chose; while running stay collapsed by default.
watch(
  () => props.running,
  (running) => {
    if (running) open.value = false;
  },
);
</script>

<template>
  <div class="reasoningRow" data-variant="think" :data-state="running ? 'running' : 'ok'" :data-expanded="open || undefined">
    <span v-if="running" class="visually-hidden">运行中</span>
    <DsDisclosureRow :open="open" title="思考" @toggle="open = !open">
      <template #icon>
        <IconThink :size="14" :stroke-width="ICON_STROKE" />
      </template>
      <template #summary>
        <span v-if="summary" class="separator" aria-hidden="true" />
        <span v-if="summary" class="summary" :data-follow-end="running || undefined">
          <span class="summaryText">{{ summary }}</span>
        </span>
      </template>
      <div class="thinkBody">{{ text }}</div>
    </DsDisclosureRow>
  </div>
</template>

<style scoped>
.reasoningRow {
  min-width: 0;
}

.reasoningRow:not([data-expanded]) {
  height: calc(24px + var(--dsh-content-font-delta, 0px));
}

.reasoningRow[data-state='running'] :deep(.row)::after {
  content: '';
  position: absolute;
  inset-block: 0;
  left: 0;
  width: 300px;
  background: linear-gradient(
    90deg,
    transparent 0%,
    color-mix(in srgb, var(--dsw-alias-bg-base) 60%, transparent) 55%,
    transparent 100%
  );
  animation: dsh-reasoning-row-sweep 2.6s ease-out infinite;
  pointer-events: none;
}

@keyframes dsh-reasoning-row-sweep {
  0% {
    left: -300px;
  }
  90%,
  100% {
    left: 100%;
  }
}

.separator {
  flex: none;
  width: 2px;
  height: 2px;
  margin: 0 8px;
  border-radius: 1px;
  background: var(--dsw-alias-label-caption);
}

.summary {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  color: var(--dsw-alias-label-tertiary);
  font-size: var(--dsh-content-font-size-secondary, 13px);
  line-height: calc(20px + var(--dsh-content-font-delta-secondary, 0px));
  white-space: nowrap;
}

.summaryText {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
}

.summary[data-follow-end] {
  display: flex;
  justify-content: flex-end;
}

.summary[data-follow-end] .summaryText {
  width: max-content;
  min-width: 100%;
  overflow: visible;
  text-overflow: clip;
}

.thinkBody {
  padding: 4px 0 4px calc(22px + var(--dsh-content-font-delta, 0px));
  color: var(--dsw-alias-label-tertiary);
  font-size: var(--dsh-content-font-size-secondary, 13px);
  line-height: calc(20px + var(--dsh-content-font-delta-secondary, 0px));
  white-space: pre-wrap;
  word-break: break-word;
}

@media (prefers-reduced-motion: reduce) {
  .reasoningRow[data-state='running'] :deep(.row)::after {
    animation: none;
  }
}
</style>
