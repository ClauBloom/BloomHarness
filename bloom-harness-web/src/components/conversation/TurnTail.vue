<script setup lang="ts">
import { computed } from 'vue';
import type { Usage } from '@/types/message.types';
import { useCopyFeedback } from '@/composables/useCopyFeedback';
import DsTooltip from '@/components/primitives/DsTooltip.vue';
import { IconCheck, IconCopy, IconData, ICON_STROKE } from '@/components/primitives/icons';
import { formatClock, formatTokens } from '@/utils/format';

/**
 * dsh TurnTail: the assistant action row after a closed turn — copy, usage
 * pill (when the backend reported token usage) and the clock. The latest turn
 * keeps it visible; earlier turns reveal it on hover.
 */
const props = defineProps<{ text: string; usage: Usage | null; timestamp: number; isLatestTurn: boolean }>();

const { copied, copy } = useCopyFeedback();
const clock = computed(() => (props.timestamp ? formatClock(props.timestamp) : ''));
const usageLabel = computed(() => (props.usage ? `用量 ${formatTokens(props.usage.totalTokens || props.usage.input + props.usage.output)} tok` : ''));
const usageTitle = computed(() =>
  props.usage
    ? `输入 ${formatTokens(props.usage.input)} · 输出 ${formatTokens(props.usage.output)}` +
      (props.usage.cacheRead ? ` · 缓存读取 ${formatTokens(props.usage.cacheRead)}` : '')
    : '',
);
</script>

<template>
  <div class="turnTail" :data-actions-reveal="isLatestTurn ? 'always' : 'hover'">
    <div class="actions">
      <DsTooltip :label="copied ? '复制成功' : '复制'" side="bottom">
        <button type="button" class="action" aria-label="复制" :disabled="!text" @click="copy(text)">
          <IconCheck v-if="copied" :size="15" :stroke-width="ICON_STROKE" />
          <IconCopy v-else :size="15" :stroke-width="ICON_STROKE" />
        </button>
      </DsTooltip>
      <DsTooltip v-if="usage" :label="usageTitle" side="bottom">
        <span class="trigger">
          <IconData :size="15" :stroke-width="ICON_STROKE" />
          <span class="label">{{ usageLabel }}</span>
        </span>
      </DsTooltip>
      <span v-if="clock" class="timeEnd">{{ clock }}</span>
    </div>
  </div>
</template>

<style scoped>
.turnTail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  height: calc(28px + var(--dsh-content-font-delta, 0px));
  margin-left: -6px;
}

.action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: calc(28px + var(--dsh-content-font-delta, 0px));
  height: calc(28px + var(--dsh-content-font-delta, 0px));
  padding: 6px;
  border: none;
  border-radius: 28px;
  background: transparent;
  color: var(--dsw-alias-label-tertiary);
  cursor: pointer;
}

.action:hover:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-hover);
  color: var(--dsw-alias-label-secondary);
}

.action:disabled {
  opacity: 0.4;
  cursor: default;
}

.trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: calc(28px + var(--dsh-content-font-delta, 0px));
  padding: 6px 8px;
  margin-left: -6px;
  border: none;
  border-radius: 28px;
  background: transparent;
  color: var(--dsw-alias-label-tertiary);
  font-size: var(--dsh-content-font-size-secondary, 13px);
  font-variant-numeric: tabular-nums;
  line-height: calc(24px + var(--dsh-content-font-delta, 0px));
  white-space: nowrap;
}

.trigger:hover {
  background: var(--dsw-alias-interactive-bg-hover);
  color: var(--dsw-alias-label-secondary);
}

.timeEnd {
  font-size: var(--dsh-content-font-size-secondary, 13px);
  line-height: calc(24px + var(--dsh-content-font-delta, 0px));
  color: var(--dsw-alias-label-tertiary);
  white-space: nowrap;
}

@media (hover: hover) {
  .turnTail[data-actions-reveal='hover'] .actions {
    opacity: 0;
    transition: opacity 80ms ease;
  }

  .turnTail[data-actions-reveal='hover']:hover .actions,
  .turnTail[data-actions-reveal='hover']:focus-within .actions {
    opacity: 1;
  }
}
</style>
