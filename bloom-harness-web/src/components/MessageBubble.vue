<script setup lang="ts">
import { AgentMessage, AssistantMessage, UserMessage, ToolResultMessage, ToolCallContent } from '@/types/message.types';
import MarkdownRenderer from './MarkdownRenderer.vue';
import ToolExecution from './ToolExecution.vue';
import { User, Bot, Brain } from 'lucide-vue-next';

defineProps<{ message: AgentMessage }>();
</script>

<template>
  <div class="flex gap-3 my-4 group" :class="{ 'justify-end': message.role === 'user' }">
    <!-- Avatar -->
    <div v-if="message.role !== 'user'" class="w-8 h-8 rounded-full bg-purple-900/60 border border-purple-500/30 flex items-center justify-center shrink-0">
      <Bot class="w-4 h-4 text-purple-300" />
    </div>

    <!-- Content Card -->
    <div 
      class="max-w-[85%] rounded-2xl px-4 py-3 shadow-md transition"
      :class="[
        message.role === 'user' 
          ? 'bg-purple-600 text-white rounded-br-sm' 
          : (message as AssistantMessage).status === 'error'
            ? 'bg-rose-950/40 border border-rose-800/60 text-rose-200 rounded-bl-sm shadow-rose-950/20'
            : 'bg-gray-900 border border-gray-800 text-gray-200 rounded-bl-sm'
      ]"
    >
      <!-- Error Status Indicator Header -->
      <div v-if="(message as AssistantMessage).status === 'error'" class="flex items-center gap-1.5 text-xs font-semibold text-rose-400 mb-2 pb-1.5 border-b border-rose-800/40">
        <span class="w-2 h-2 rounded-full bg-rose-500 animate-pulse"></span>
        <span>服务调用异常</span>
      </div>

      <!-- Message Content Loop -->
      <div v-for="(item, idx) in message.content" :key="idx">
        <!-- Text -->
        <MarkdownRenderer v-if="item.type === 'text'" :content="item.text" />

        <!-- Thinking / Reasoning -->
        <div v-else-if="item.type === 'thinking'" class="my-2 p-2.5 rounded-lg bg-gray-950/70 border border-purple-900/40 text-xs text-purple-300 font-mono">
          <div class="flex items-center gap-1.5 font-semibold text-purple-400 mb-1">
            <Brain class="w-3.5 h-3.5 animate-pulse" />
            <span>思考过程 (Reasoning)</span>
          </div>
          <div class="whitespace-pre-wrap leading-relaxed opacity-90">{{ item.thinking }}</div>
        </div>

        <!-- Tool Call -->
        <ToolExecution v-else-if="item.type === 'toolCall'" :toolCall="(item as ToolCallContent)" />
      </div>

      <!-- Tool Result Message -->
      <ToolExecution v-if="message.role === 'tool'" :toolResult="(message as ToolResultMessage)" />
    </div>

    <!-- User Avatar -->
    <div v-if="message.role === 'user'" class="w-8 h-8 rounded-full bg-gray-800 border border-gray-700 flex items-center justify-center shrink-0">
      <User class="w-4 h-4 text-gray-300" />
    </div>
  </div>
</template>
