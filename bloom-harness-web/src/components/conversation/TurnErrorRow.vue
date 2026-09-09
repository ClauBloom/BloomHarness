<script setup lang="ts">
import type { AssistantMessage } from '@/types/message.types';
import DsStateDot from '@/components/primitives/DsStateDot.vue';

/** dsh turn-error row: red state dot, "本轮运行失败" and the backend message. */
defineProps<{ message: AssistantMessage }>();
</script>

<template>
  <div class="turnErrorRow" role="status">
    <DsStateDot class="dot" state="error" />
    <span class="body">
      <span class="turnErrorTitle">本轮运行失败</span>
      <span class="text">{{ message.errorMessage || '模型执行过程出现异常' }}</span>
    </span>
  </div>
</template>

<style scoped>
.turnErrorRow {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr);
  gap: 8px;
  padding: 2px 0;
  font-size: 13px;
  line-height: 20px;
}

.dot {
  margin-top: 5px;
}

.body {
  min-width: 0;
  overflow-wrap: anywhere;
}

.turnErrorTitle {
  margin-right: 6px;
  color: var(--dsw-alias-state-error-primary);
  font-weight: 600;
}

.text {
  color: var(--dsw-alias-label-secondary);
  white-space: pre-wrap;
}
</style>
