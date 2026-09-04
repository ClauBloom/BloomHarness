<script setup lang="ts">
import { computed } from 'vue';
import { renderMarkdown } from '@/utils/formatMarkdown';

const props = defineProps<{ content: string }>();
const renderedHtml = computed(() => renderMarkdown(props.content));
</script>

<template>
  <div class="prose prose-invert max-w-none text-sm leading-relaxed" v-html="renderedHtml"></div>
</template>
