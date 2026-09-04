<script setup lang="ts">
import { ref, computed } from 'vue';
import { ToolCallContent, ToolResultMessage } from '@/types/message.types';
import { 
  ChevronDown, 
  ChevronRight, 
  Terminal, 
  Wrench, 
  CheckCircle2, 
  AlertCircle,
  Copy,
  Check,
  Maximize2,
  Minimize2,
  FileText
} from 'lucide-vue-next';

const props = defineProps<{
  toolCall?: ToolCallContent;
  toolResult?: ToolResultMessage;
}>();

const isOpen = ref(false);
const isExpanded = ref(false);
const copied = ref(false);

const rawOutput = computed(() => {
  if (!props.toolResult?.content) return '';
  return props.toolResult.content
    .map(c => (c.type === 'text' ? c.text : ''))
    .join('\n');
});

// 安全阈值：超过 120 行或超过 15KB 则启用智能截断
const MAX_PREVIEW_LINES = 120;
const MAX_PREVIEW_BYTES = 15 * 1024;

const outputStats = computed(() => {
  const text = rawOutput.value;
  const lines = text.split('\n');
  const isTooLarge = lines.length > MAX_PREVIEW_LINES || text.length > MAX_PREVIEW_BYTES;
  return {
    lineCount: lines.length,
    byteSize: text.length,
    isTooLarge,
  };
});

const displayedOutput = computed(() => {
  const text = rawOutput.value;
  if (!outputStats.value.isTooLarge || isExpanded.value) {
    return text;
  }
  const lines = text.split('\n');
  const head = lines.slice(0, 80).join('\n');
  const tail = lines.slice(-30).join('\n');
  const omitted = lines.length - 110;
  return `${head}\n\n... [💡 已自动折叠中间 ${omitted > 0 ? omitted : '若干'} 行超长输出，共 ${formatBytes(text.length)}。点击下方“展开全部”查看完整内容] ...\n\n${tail}`;
});

function formatBytes(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
}

async function copyFullOutput() {
  if (!rawOutput.value) return;
  try {
    await navigator.clipboard.writeText(rawOutput.value);
    copied.value = true;
    setTimeout(() => { copied.value = false; }, 2000);
  } catch (e) {
    console.warn('Copy failed:', e);
  }
}
</script>

<template>
  <div class="my-2 rounded-xl border border-gray-800 bg-gray-900/90 overflow-hidden text-xs shadow-sm">
    <!-- Collapsible Header -->
    <div 
      @click="isOpen = !isOpen"
      class="flex items-center justify-between px-3.5 py-2.5 bg-gray-800/50 cursor-pointer hover:bg-gray-800/80 transition select-none"
    >
      <div class="flex items-center gap-2 min-w-0">
        <component :is="toolCall?.toolName === 'bash' ? Terminal : Wrench" class="w-4 h-4 text-purple-400 shrink-0" />
        <span class="font-mono font-semibold text-purple-300 shrink-0">
          {{ toolCall?.toolName || toolResult?.toolName }}
        </span>
        <span class="text-gray-500 font-mono text-[11px] truncate max-w-sm">
          {{ toolCall?.input ? JSON.stringify(toolCall.input) : '' }}
        </span>
      </div>

      <div class="flex items-center gap-2.5 shrink-0">
        <span v-if="toolResult" class="flex items-center gap-1">
          <CheckCircle2 v-if="!toolResult.isError" class="w-3.5 h-3.5 text-emerald-400" />
          <AlertCircle v-else class="w-3.5 h-3.5 text-rose-400" />
          <span :class="toolResult.isError ? 'text-rose-400 font-medium' : 'text-emerald-400 font-medium'">
            {{ toolResult.isError ? '执行失败' : '完成' }}
          </span>
          <span v-if="outputStats.isTooLarge" class="text-[10px] font-mono text-zinc-500 px-1 py-0.5 rounded bg-zinc-800 ml-1">
            {{ formatBytes(outputStats.byteSize) }}
          </span>
        </span>
        <span v-else class="text-amber-400 animate-pulse flex items-center gap-1 font-medium">
          <span class="w-1.5 h-1.5 rounded-full bg-amber-400 animate-ping"></span>
          执行中...
        </span>
        <component :is="isOpen ? ChevronDown : ChevronRight" class="w-3.5 h-3.5 text-gray-400" />
      </div>
    </div>
    
    <!-- Expanded Detail Body -->
    <div v-if="isOpen" class="p-3.5 border-t border-gray-800/80 space-y-3 bg-black/40">
      <!-- Tool Input -->
      <div v-if="toolCall?.input">
        <div class="text-gray-400 mb-1 font-semibold flex items-center gap-1">
          <span>输入参数:</span>
        </div>
        <pre class="p-2.5 bg-gray-950/80 border border-gray-800/80 rounded-lg text-gray-300 font-mono overflow-x-auto max-h-48 leading-relaxed">{{ JSON.stringify(toolCall.input, null, 2) }}</pre>
      </div>

      <!-- Tool Execution Output -->
      <div v-if="toolResult">
        <div class="flex items-center justify-between mb-1.5">
          <div class="text-gray-400 font-semibold flex items-center gap-1.5">
            <FileText class="w-3.5 h-3.5 text-purple-400" />
            <span>执行输出</span>
            <span class="text-[10px] text-zinc-500 font-mono">
              ({{ outputStats.lineCount }} 行, {{ formatBytes(outputStats.byteSize) }})
            </span>
          </div>

          <!-- Output Actions -->
          <div class="flex items-center gap-2">
            <button 
              type="button"
              @click.stop="copyFullOutput"
              class="px-2 py-0.5 rounded bg-zinc-800 hover:bg-zinc-700 text-zinc-300 text-[10px] font-mono flex items-center gap-1 transition"
              title="复制全部输出文本"
            >
              <Check v-if="copied" class="w-3 h-3 text-emerald-400" />
              <Copy v-else class="w-3 h-3 text-zinc-400" />
              <span>{{ copied ? '已复制' : '复制全部' }}</span>
            </button>

            <button 
              v-if="outputStats.isTooLarge"
              type="button"
              @click.stop="isExpanded = !isExpanded"
              class="px-2 py-0.5 rounded bg-purple-950/60 hover:bg-purple-900/70 border border-purple-800/50 text-purple-300 text-[10px] font-mono flex items-center gap-1 transition"
            >
              <component :is="isExpanded ? Minimize2 : Maximize2" class="w-3 h-3" />
              <span>{{ isExpanded ? '收起长文本' : '展开全部' }}</span>
            </button>
          </div>
        </div>

        <!-- Preformatted Output Box with Max Height -->
        <pre 
          class="p-3 bg-gray-950/90 border border-gray-800/80 rounded-lg font-mono overflow-x-auto text-emerald-300 text-[11px] leading-relaxed max-h-[380px] overflow-y-auto"
          :class="{ 'text-rose-300': toolResult.isError }"
        >{{ displayedOutput }}</pre>
      </div>
    </div>
  </div>
</template>
