<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { renderMarkdown } from '@/utils/formatMarkdown';
import { ensureGrammar, highlightToHtml, onGrammarLoaded } from '@/utils/highlight';
import { useCopyFeedback, COPY_LABEL, COPIED_LABEL } from '@/composables/useCopyFeedback';

/**
 * Markdown host for assistant text (dsh AssistantMarkdown + MarkdownText):
 * renders through utils/formatMarkdown.ts, throttles re-renders while
 * streaming, and highlights settled fenced code with shiki in place.
 */
const props = withDefaults(defineProps<{ text: string; streaming?: boolean }>(), { streaming: false });

const host = ref<HTMLElement | null>(null);
const html = ref('');
const { copy } = useCopyFeedback();

const STREAM_THROTTLE_MS = 50;
let throttle: ReturnType<typeof setTimeout> | null = null;
let dirty = false;

function render() {
  html.value = renderMarkdown(props.text);
  dirty = false;
  void nextTick(highlight);
}

function scheduleRender() {
  if (!props.streaming) {
    if (throttle) {
      clearTimeout(throttle);
      throttle = null;
    }
    render();
    return;
  }
  dirty = true;
  if (throttle) return;
  throttle = setTimeout(() => {
    throttle = null;
    if (dirty) render();
  }, STREAM_THROTTLE_MS);
}

watch(() => [props.text, props.streaming] as const, scheduleRender, { immediate: true });

function highlight() {
  if (props.streaming || !host.value) return;
  const blocks = host.value.querySelectorAll<HTMLElement>('.md-code-block[data-lang]');
  blocks.forEach((block) => {
    const pre = block.querySelector('pre');
    if (!pre || pre.classList.contains('shiki')) return;
    const lang = block.dataset.lang;
    const code = pre.textContent ?? '';
    const out = highlightToHtml(code.replace(/\n$/, ''), lang);
    if (out) {
      pre.outerHTML = out.replace(/\s+tabindex="0"/g, '');
    } else {
      void ensureGrammar(lang).then((ok) => ok && highlight());
    }
  });
}

const offGrammar = onGrammarLoaded(() => void nextTick(highlight));
onBeforeUnmount(() => {
  offGrammar();
  if (throttle) clearTimeout(throttle);
});

async function onClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null;
  const button = target?.closest<HTMLElement>('[data-md-copy]');
  if (!button || !host.value?.contains(button)) return;
  const block = button.closest('.md-code-block');
  const pre = block?.querySelector('pre');
  if (!pre) return;
  const ok = await copy((pre.textContent ?? '').replace(/\n$/, ''));
  if (!ok) return;
  button.textContent = COPIED_LABEL;
  setTimeout(() => {
    button.textContent = COPY_LABEL;
  }, 1000);
}
</script>

<template>
  <div ref="host" class="markdown" :data-streaming="streaming || undefined" @click="onClick" v-html="html" />
</template>
