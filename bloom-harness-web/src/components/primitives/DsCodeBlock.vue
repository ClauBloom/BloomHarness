<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useCopyFeedback, COPY_LABEL, COPIED_LABEL } from '@/composables/useCopyFeedback';
import { escapeHtml } from '@/utils/formatMarkdown';
import { ensureGrammar, highlightToHtml, onGrammarLoaded, resolveLang } from '@/utils/highlight';

/**
 * dsh CodeBlock primitive (banner with language + copy, wrapped pre). Used for
 * standalone code surfaces (tool payloads); markdown fences render the same DOM
 * through utils/formatMarkdown.ts + styles/markdown.css.
 */
const props = withDefaults(
  defineProps<{
    code: string;
    lang?: string;
    /** Banner text; defaults to the language hint. */
    label?: string;
    /** Compact 11/16 font (tool rows). */
    small?: boolean;
    maxHeight?: number;
  }>(),
  { lang: undefined, label: undefined, small: false, maxHeight: undefined },
);

const { copied, copy } = useCopyFeedback();
const display = computed(() => props.code.replace(/\n$/, ''));
const html = ref<string | null>(null);

function render() {
  const lang = resolveLang(props.lang);
  if (!lang) {
    html.value = null;
    return;
  }
  const out = highlightToHtml(display.value, lang);
  if (out) {
    html.value = out;
  } else {
    html.value = null;
    void ensureGrammar(lang).then((ok) => ok && render());
  }
}

const off = onGrammarLoaded(() => render());
onBeforeUnmount(off);
watch([() => props.code, () => props.lang], render, { immediate: true });

const plainHtml = computed(() => `<pre class="plain"><code>${escapeHtml(display.value)}</code></pre>`);
</script>

<template>
  <div class="block" :class="{ small }">
    <div class="bannerWrap">
      <div class="banner">
        <div class="infostring">{{ label ?? lang ?? '' }}</div>
        <div class="action">
          <button type="button" class="copyButton" @click="copy(code)">{{ copied ? COPIED_LABEL : COPY_LABEL }}</button>
        </div>
      </div>
    </div>
    <div class="content" :style="maxHeight ? { maxHeight: `${maxHeight}px`, overflowY: 'auto' } : undefined" v-html="html ?? plainHtml" />
  </div>
</template>

<style scoped>
.block {
  --dsl-code-block-border-radius: 12px;
  --dsl-code-block-content-font: var(--dsw-font-markdown-code-block);
  position: relative;
  margin: 0;
  color: var(--dsw-alias-label-primary);
  background: var(--dsw-alias-markdown-code-block);
  border-radius: var(--dsl-code-block-border-radius);
}

.small {
  --dsl-code-block-content-font: var(--dsw-font-markdown-code-block-small);
}

.bannerWrap {
  position: sticky;
  top: 0;
  z-index: 6;
  border-top-left-radius: var(--dsl-code-block-border-radius);
  border-top-right-radius: var(--dsl-code-block-border-radius);
}

.banner {
  background: var(--dsw-alias-markdown-code-block-banner);
  padding: 9px 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  font: 11px/18px var(--dsw-font-family);
  border-top-left-radius: var(--dsl-code-block-border-radius);
  border-top-right-radius: var(--dsl-code-block-border-radius);
}

.infostring {
  color: var(--dsw-alias-label-primary);
  font-family: var(--ds-font-family-code);
  font-size: 11px;
  line-height: 18px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.action {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.copyButton {
  background: transparent;
  border: none;
  padding: 0;
  margin: 0;
  color: inherit;
  cursor: pointer;
  font: inherit;
}

.copyButton:hover {
  color: var(--dsw-alias-label-secondary);
}

.content :deep(pre) {
  font: var(--dsl-code-block-content-font);
  padding: 16px;
  margin: 0 !important;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  background: var(--dsw-alias-markdown-code-block) !important;
  border-bottom-left-radius: var(--dsl-code-block-border-radius);
  border-bottom-right-radius: var(--dsl-code-block-border-radius);
}

.content :deep(pre code) {
  font: inherit;
  background: none;
  padding: 0;
}

.content :deep(.plain) {
  color: var(--dsw-alias-label-primary);
}
</style>
