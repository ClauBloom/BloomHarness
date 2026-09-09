<script setup lang="ts">
import { computed } from 'vue';
import type { UserMessage } from '@/types/message.types';
import { useCopyFeedback } from '@/composables/useCopyFeedback';
import DsTooltip from '@/components/primitives/DsTooltip.vue';
import { IconCheck, IconCopy, ICON_STROKE } from '@/components/primitives/icons';
import { formatClock } from '@/utils/format';

/**
 * dsh user bubble: right-aligned, `--dsw-specific-bubble` fill, r22, 10/16
 * padding; clock + copy action underneath (latest message keeps it visible,
 * earlier ones reveal it on hover).
 */
const props = defineProps<{ message: UserMessage; text: string; isLatest: boolean }>();

const { copied, copy } = useCopyFeedback();
const clock = computed(() => formatClock(props.message.timestamp));
const images = computed(() =>
  props.message.content.filter((c): c is { type: 'image'; data: string; mimeType: string } => c.type === 'image'),
);
</script>

<template>
  <div class="userRow" :data-actions-reveal="isLatest ? 'always' : 'hover'">
    <div class="userStack">
      <div v-if="images.length" class="attachmentRow">
        <span v-for="(img, i) in images" :key="i" class="frame" :data-variant="images.length > 1 ? 'tile' : 'single'">
          <img :src="`data:${img.mimeType};base64,${img.data}`" alt="图片" />
        </span>
      </div>
      <div v-if="text" class="bubble">{{ text }}</div>
    </div>
    <div class="actions">
      <span class="timeStart">{{ clock }}</span>
      <DsTooltip :label="copied ? '复制成功' : '复制'" side="bottom">
        <button type="button" class="action" aria-label="复制" @click="copy(text)">
          <IconCheck v-if="copied" :size="15" :stroke-width="ICON_STROKE" />
          <IconCopy v-else :size="15" :stroke-width="ICON_STROKE" />
        </button>
      </DsTooltip>
    </div>
  </div>
</template>

<style scoped>
.userRow {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.userStack {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  min-width: 0;
  max-width: min(calc(var(--dsh-chat-content-width, 748px) * 0.702), 82%);
}

.bubble {
  max-width: 100%;
  box-sizing: border-box;
  background: var(--dsw-specific-bubble);
  border-radius: 22px;
  padding: 10px 16px;
  font-size: var(--dsh-content-font-size, 14px);
  line-height: calc(22px + var(--dsh-content-font-delta, 0px));
  color: var(--dsw-alias-label-primary);
  white-space: pre-wrap;
  word-break: break-word;
}

.attachmentRow {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  max-width: 100%;
  gap: 8px;
}

.frame {
  display: grid;
  place-items: center;
  overflow: hidden;
  border: 0.5px solid var(--dsw-alias-border-l2-darkmode-thin);
  border-radius: 16px;
  background: var(--dsw-alias-interactive-bg-hover);
}

.frame[data-variant='single'] img {
  display: block;
  max-width: 240px;
  max-height: 240px;
}

.frame[data-variant='tile'] {
  width: 64px;
  height: 64px;
}

.frame[data-variant='tile'] img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  height: calc(28px + var(--dsh-content-font-delta, 0px));
}

.timeStart {
  padding-right: 12px;
  font-size: var(--dsh-content-font-size-secondary, 13px);
  line-height: calc(24px + var(--dsh-content-font-delta, 0px));
  color: var(--dsw-alias-label-tertiary);
  white-space: nowrap;
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

.action:hover {
  background: var(--dsw-alias-interactive-bg-hover);
  color: var(--dsw-alias-label-secondary);
}

@media (hover: hover) {
  .userRow[data-actions-reveal='hover'] .actions {
    opacity: 0;
    transition: opacity 80ms ease;
  }

  .userRow[data-actions-reveal='hover']:hover .actions,
  .userRow[data-actions-reveal='hover']:focus-within .actions {
    opacity: 1;
  }
}
</style>
