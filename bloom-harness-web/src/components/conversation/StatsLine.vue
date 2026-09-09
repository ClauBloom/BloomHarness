<script setup lang="ts">
import { computed } from 'vue';
import { useChatFlow } from '@/composables/useChatFlow';
import { formatTokens } from '@/utils/format';

/**
 * dsh StatsLine: one centred pipe-separated line under the docked composer.
 * Groups drop out when empty; hidden entirely before the first turn.
 */
const { stats } = useChatFlow();

const groups = computed(() => {
  const s = stats.value;
  const out: string[] = [];
  if (s.turns > 0) out.push(`${s.turns} 轮 · ${s.steps} 步`);
  if (s.inputTokens > 0 || s.outputTokens > 0) {
    out.push(`输入 ${formatTokens(s.inputTokens)} tok · 输出 ${formatTokens(s.outputTokens)} tok`);
  }
  return out;
});
</script>

<template>
  <div v-if="groups.length" class="statsLine" :title="groups.join(' | ')">
    <template v-for="(group, i) in groups" :key="i">
      <span v-if="i > 0" class="sep" aria-hidden="true">|</span>
      <span>{{ group }}</span>
    </template>
  </div>
</template>

<style scoped>
.statsLine {
  display: block;
  box-sizing: border-box;
  text-align: center;
  max-width: var(--dsh-chat-content-width, 748px);
  width: 100%;
  margin: 0 auto;
  padding: 4px calc(var(--dsh-composer-side-clearance, 16px) + 16px) 0;
  font-size: var(--dsh-content-font-size-secondary, 13px);
  line-height: calc(20px + var(--dsh-content-font-delta-secondary, 0px));
  color: var(--dsw-alias-label-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sep {
  color: var(--dsw-alias-border-l4);
  margin: 0 10px;
}
</style>
