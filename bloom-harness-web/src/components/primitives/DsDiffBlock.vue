<script setup lang="ts">
import { computed, ref } from 'vue';
import { useCopyFeedback, COPY_LABEL, COPIED_LABEL } from '@/composables/useCopyFeedback';

/**
 * dsh DiffBlock: path header, `-` old lines then `+` new lines, floating copy
 * button, `└ +A -R · n 个文件` footer; head/tail folded past `maxLines`.
 */
const props = withDefaults(
  defineProps<{ path: string; oldText: string | null; newText: string; maxLines?: number }>(),
  { maxLines: 8 },
);

const expanded = ref(false);
const { copied, copy } = useCopyFeedback();

type Row = { kind: 'path' | 'del' | 'add'; text: string };

function splitLines(text: string | null): string[] {
  if (!text) return [];
  const lines = text.split('\n');
  if (lines[lines.length - 1] === '') lines.pop();
  return lines;
}

const rows = computed<Row[]>(() => {
  const out: Row[] = [{ kind: 'path', text: props.path }];
  for (const l of splitLines(props.oldText)) out.push({ kind: 'del', text: l });
  for (const l of splitLines(props.newText)) out.push({ kind: 'add', text: l });
  return out;
});

const totals = computed(() => ({
  added: rows.value.filter((r) => r.kind === 'add').length,
  removed: rows.value.filter((r) => r.kind === 'del').length,
}));

const fold = computed(() => {
  const all = rows.value;
  const hidden = all.length - props.maxLines;
  if (hidden <= 0 || expanded.value) return { capped: false, head: all, tail: [] as Row[], hidden: 0 };
  const headLines = Math.ceil(props.maxLines / 2);
  const tailLines = props.maxLines - headLines;
  return {
    capped: true,
    head: all.slice(0, headLines),
    tail: tailLines > 0 ? all.slice(all.length - tailLines) : [],
    hidden,
  };
});

const copyText = computed(() =>
  rows.value
    .map((r) => (r.kind === 'del' ? `- ${r.text}` : r.kind === 'add' ? `+ ${r.text}` : r.text))
    .join('\n'),
);
</script>

<template>
  <div class="block" data-diff>
    <button type="button" class="copyButton" @click="copy(copyText)">{{ copied ? COPIED_LABEL : COPY_LABEL }}</button>
    <div class="body">
      <div v-for="(row, i) in fold.head" :key="`h${i}`" :class="['line', row.kind]">{{ row.text }}</div>
      <button
        v-if="fold.capped"
        type="button"
        class="expand"
        :aria-label="`展开其余 ${fold.hidden} 行差异`"
        @click="expanded = true"
      >
        … 其余 {{ fold.hidden }} 行
      </button>
      <div v-for="(row, i) in fold.tail" :key="`t${i}`" :class="['line', row.kind]">{{ row.text }}</div>
    </div>
    <div class="footer">└ +{{ totals.added }} -{{ totals.removed }} · 1 个文件</div>
  </div>
</template>

<style scoped>
.block {
  --dsl-diff-radius: 12px;
  --dsl-diff-line-height: 22px;
  position: relative;
  margin: 0;
  color: var(--dsw-alias-label-primary);
  background: var(--dsw-alias-markdown-code-block);
  border-radius: var(--dsl-diff-radius);
}

.copyButton {
  position: absolute;
  top: 8px;
  right: 12px;
  z-index: 1;
  background-color: transparent;
  border: none;
  padding: 0;
  margin: 0;
  color: var(--dsw-alias-label-secondary);
  cursor: pointer;
  font: var(--dsw-font-xs-13);
}

.copyButton:hover {
  color: var(--dsw-alias-label-primary);
}

.body {
  padding: 12px 14px;
  font: var(--dsw-font-markdown-code-block);
  overflow-x: auto;
  overflow-y: hidden;
}

.line {
  min-height: var(--dsl-diff-line-height);
  white-space: pre;
}

.path {
  color: var(--dsw-alias-label-primary);
  font-weight: 600;
  padding-right: 56px;
}

.del {
  color: var(--dsw-alias-state-error-primary);
}

.del::before {
  content: '- ';
}

.add {
  color: var(--dsw-alias-state-success-primary);
}

.add::before {
  content: '+ ';
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

.footer {
  padding: 0 14px 12px;
  font: var(--dsw-font-markdown-code-block);
  color: var(--dsw-alias-label-tertiary);
}
</style>
