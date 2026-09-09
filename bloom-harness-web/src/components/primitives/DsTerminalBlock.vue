<script setup lang="ts">
import { computed, ref } from 'vue';
import { useCopyFeedback, COPY_LABEL, COPIED_LABEL } from '@/composables/useCopyFeedback';
import DsStateDot from './DsStateDot.vue';
import DsPill from './DsPill.vue';

/**
 * dsh TerminalBlock: prompt banner (run-state dot in a 30px gutter, cwd label,
 * command), optional exit-code pill, and the output with a head/tail fold.
 */
const props = withDefaults(
  defineProps<{
    command: string;
    /** Prompt label (`~` or the working directory's last segment). */
    cwd?: string;
    output?: string;
    exitCode?: number | null;
    running?: boolean;
    /** Fold threshold; `Infinity` disables the fold (the output scrolls instead). */
    maxLines?: number;
    outputMaxHeight?: number;
    compact?: boolean;
  }>(),
  { cwd: undefined, output: '', exitCode: null, running: false, maxLines: 16, outputMaxHeight: undefined, compact: false },
);

const expanded = ref(false);
const { copied, copy } = useCopyFeedback();

const commandLines = computed(() => props.command.split('\n'));
const lines = computed(() => {
  const text = props.output ?? '';
  if (!text) return [];
  const list = text.split('\n');
  if (list[list.length - 1] === '') list.pop();
  return list;
});

const fold = computed(() => {
  const total = lines.value.length;
  const hidden = total - props.maxLines;
  if (!Number.isFinite(props.maxLines) || hidden <= 0 || expanded.value) {
    return { capped: false, head: lines.value, tail: [] as string[], hidden: 0 };
  }
  const headLines = Math.ceil(props.maxLines / 2);
  const tailLines = props.maxLines - headLines;
  return {
    capped: true,
    head: lines.value.slice(0, headLines),
    tail: tailLines > 0 ? lines.value.slice(total - tailLines) : [],
    hidden,
  };
});

const runState = computed(() => (props.running ? 'ongoing' : props.exitCode ? 'error' : 'done'));
const failed = computed(() => !props.running && !!props.exitCode);
</script>

<template>
  <div class="block" :class="{ compact }" data-terminal :data-running="running || undefined">
    <div class="header">
      <div class="prompt">
        <span class="visually-hidden">{{ running ? '运行中' : failed ? '失败' : '已完成' }}</span>
        <div v-for="(line, i) in commandLines" :key="i" class="promptLine">
          <DsStateDot v-if="i === 0" class="runState" :state="runState" />
          <span class="cwd">{{ i === 0 ? (cwd || '~') : '$' }}</span>
          <span class="command">{{ line }}</span>
        </div>
      </div>
      <DsPill v-if="failed" class="status" tone="error">退出码 {{ exitCode }}</DsPill>
      <button v-if="!running && lines.length" type="button" class="copyButton" @click="copy(output ?? '')">
        {{ copied ? COPIED_LABEL : COPY_LABEL }}
      </button>
    </div>
    <template v-if="!running">
      <div v-if="lines.length === 0" class="empty">无输出</div>
      <div v-else class="output" :style="outputMaxHeight ? { maxHeight: `${outputMaxHeight}px` } : undefined">
        <div v-for="(line, i) in fold.head" :key="`h${i}`" class="line">{{ line }}</div>
        <button
          v-if="fold.capped"
          type="button"
          class="expand"
          :aria-expanded="false"
          :aria-label="`展开其余 ${fold.hidden} 行输出`"
          @click="expanded = true"
        >
          … 其余 {{ fold.hidden }} 行
        </button>
        <div v-for="(line, i) in fold.tail" :key="`t${i}`" class="line">{{ line }}</div>
        <button
          v-if="!fold.capped && expanded && lines.length > maxLines"
          type="button"
          class="expand"
          :aria-expanded="true"
          aria-label="收起输出"
          @click="expanded = false"
        >
          收起
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.block {
  --dsl-terminal-radius: 12px;
  --dsl-terminal-line-height: 22px;
  --dsl-terminal-font: var(--dsw-font-markdown-code-block);
  --dsl-terminal-gutter: 30px;
  position: relative;
  margin: 0;
  padding-left: var(--dsl-terminal-gutter);
  color: var(--dsw-alias-label-primary);
  background: var(--dsw-alias-markdown-code-block);
  border-radius: var(--dsl-terminal-radius);
  overflow: hidden;
}

.compact {
  --dsl-terminal-font: var(--dsw-font-markdown-code-block-small);
  --dsl-terminal-line-height: 18px;
  border: 0.5px solid var(--dsw-alias-border-l1);
}

.header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-left: calc(-1 * var(--dsl-terminal-gutter));
  padding: 9px 14px 9px var(--dsl-terminal-gutter);
  border-top-left-radius: var(--dsl-terminal-radius);
  border-top-right-radius: var(--dsl-terminal-radius);
  max-height: 150px;
  overflow-y: auto;
}

.block:not([data-running]) .header {
  border-bottom: 0.5px solid var(--dsw-alias-border-l2);
}

.prompt {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
  font: var(--dsl-terminal-font);
}

.promptLine {
  position: relative;
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
  line-height: var(--dsl-terminal-line-height);
}

.runState {
  position: absolute;
  left: calc(-1 * var(--dsl-terminal-gutter) + 8px);
  top: 50%;
  transform: translateY(-50%);
}

.cwd {
  flex: none;
  color: var(--dsw-alias-label-tertiary);
}

.command {
  min-width: 0;
  color: var(--dsw-alias-label-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: pre;
}

.status {
  flex: none;
  position: sticky;
  top: 0;
  height: var(--dsl-terminal-line-height);
}

.copyButton {
  flex: none;
  position: sticky;
  top: 0;
  background-color: var(--dsw-alias-markdown-code-block);
  border: none;
  padding: 0;
  margin: 0;
  color: var(--dsw-alias-label-secondary);
  cursor: pointer;
  font: var(--dsw-font-xs-13);
  line-height: var(--dsl-terminal-line-height);
}

.copyButton:hover {
  color: var(--dsw-alias-label-primary);
}

.output {
  padding: 12px 14px 12px 0;
  font: var(--dsl-terminal-font);
  overflow-x: auto;
  overflow-y: auto;
}

.line {
  min-height: var(--dsl-terminal-line-height);
  white-space: pre;
}

.expand {
  display: block;
  width: 100%;
  padding: 0;
  border: none;
  background-color: transparent;
  color: var(--dsw-alias-label-tertiary);
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.expand:hover {
  color: var(--dsw-alias-label-secondary);
}

.empty {
  padding: 12px 14px 12px 0;
  font: var(--dsl-terminal-font);
  color: var(--dsw-alias-label-tertiary);
}
</style>
