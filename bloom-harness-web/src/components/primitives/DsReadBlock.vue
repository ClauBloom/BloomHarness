<script setup lang="ts">
import { computed, ref } from 'vue';
import { useCopyFeedback, COPY_LABEL, COPIED_LABEL } from '@/composables/useCopyFeedback';

/**
 * dsh ReadBlock: banner (path + line window + copy) and numbered lines with a
 * 48px gutter, head/tail folded past `maxLines`. Parses BloomHarness ReadTool
 * output (`%4d: text` per line, optional trailing `[…]` notes).
 */
const props = withDefaults(
  defineProps<{ label: string; text: string; maxLines?: number }>(),
  { maxLines: 8 },
);

const expanded = ref(false);
const { copied, copy } = useCopyFeedback();

interface ReadLine {
  number: string;
  text: string;
}

const parsed = computed(() => {
  const lines: ReadLine[] = [];
  const notes: string[] = [];
  for (const raw of props.text.split('\n')) {
    const m = /^\s*(\d+): (.*)$/.exec(raw);
    if (m) {
      lines.push({ number: m[1]!, text: m[2]! });
    } else if (raw.trim()) {
      notes.push(raw.trim());
    }
  }
  return { lines, notes };
});

const fold = computed(() => {
  const all = parsed.value.lines;
  const hidden = all.length - props.maxLines;
  if (hidden <= 0 || expanded.value) return { capped: false, head: all, tail: [] as ReadLine[], hidden: 0 };
  const headLines = Math.ceil(props.maxLines / 2);
  const tailLines = props.maxLines - headLines;
  return {
    capped: true,
    head: all.slice(0, headLines),
    tail: tailLines > 0 ? all.slice(all.length - tailLines) : [],
    hidden,
  };
});

const copyText = computed(() => parsed.value.lines.map((l) => l.text).join('\n'));
</script>

<template>
  <div class="block" data-read>
    <div class="banner">
      <div class="label">{{ label }}</div>
      <div class="action">
        <span v-if="parsed.lines.length" class="count">{{ parsed.lines.length }} 行</span>
        <button v-if="parsed.lines.length" type="button" class="copyButton" @click="copy(copyText)">
          {{ copied ? COPIED_LABEL : COPY_LABEL }}
        </button>
      </div>
    </div>
    <div class="body">
      <template v-if="parsed.lines.length">
        <div v-for="line in fold.head" :key="`h${line.number}`" class="line">
          <span class="gutter" aria-hidden="true">{{ line.number }}</span>
          <span class="content">{{ line.text }}</span>
        </div>
        <button
          v-if="fold.capped"
          type="button"
          class="expand"
          :aria-label="`展开其余 ${fold.hidden} 行`"
          @click="expanded = true"
        >
          … 其余 {{ fold.hidden }} 行
        </button>
        <div v-for="line in fold.tail" :key="`t${line.number}`" class="line">
          <span class="gutter" aria-hidden="true">{{ line.number }}</span>
          <span class="content">{{ line.text }}</span>
        </div>
      </template>
      <div v-else class="line"><span class="gutter" /><span class="content muted">{{ text.trim() || '（空文件）' }}</span></div>
      <div v-for="(note, i) in parsed.notes" :key="`n${i}`" class="note">{{ note }}</div>
    </div>
  </div>
</template>

<style scoped>
.block {
  --dsl-read-radius: 12px;
  --dsl-read-line-height: 22px;
  --dsl-read-gutter: 48px;
  position: relative;
  margin: 0;
  color: var(--dsw-alias-label-primary);
  background: var(--dsw-alias-markdown-code-block);
  border-radius: var(--dsl-read-radius);
}

.banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 9px 14px;
  background: var(--dsw-alias-markdown-code-block-banner);
  border-top-left-radius: var(--dsl-read-radius);
  border-top-right-radius: var(--dsl-read-radius);
}

.label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--dsw-alias-label-primary);
  font-family: var(--ds-font-family-code);
  font-size: 12px;
  line-height: 18px;
}

.action {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 12px;
}

.count {
  color: var(--dsw-alias-label-tertiary);
  font: var(--dsw-font-xs-13);
}

.copyButton {
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
  padding: 12px 0;
  font: var(--dsw-font-markdown-code-block);
  overflow-x: auto;
  overflow-y: hidden;
}

.line {
  display: flex;
  min-height: var(--dsl-read-line-height);
  line-height: var(--dsl-read-line-height);
  white-space: pre;
}

.gutter {
  flex: none;
  width: var(--dsl-read-gutter);
  padding-right: 14px;
  box-sizing: border-box;
  text-align: right;
  color: var(--dsw-alias-label-tertiary);
  user-select: none;
}

.content {
  color: var(--dsw-alias-label-primary);
  padding-right: 14px;
}

.muted {
  color: var(--dsw-alias-label-tertiary);
  white-space: pre-wrap;
}

.expand {
  display: block;
  width: 100%;
  padding: 0 0 0 var(--dsl-read-gutter);
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

.note {
  margin-top: 6px;
  padding: 0 14px 0 var(--dsl-read-gutter);
  color: var(--dsw-alias-label-tertiary);
  white-space: pre-wrap;
}
</style>
