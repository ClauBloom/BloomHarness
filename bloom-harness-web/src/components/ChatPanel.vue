<script setup lang="ts">
import { ref, watch, nextTick, computed, onMounted } from 'vue';
import { useAgentStore } from '@/stores/agentStore';
import { useSession } from '@/composables/useSession';
import MessageBubble from './MessageBubble.vue';
import { 
  Send, 
  Square, 
  Sparkles, 
  Loader2, 
  AlertTriangle, 
  FolderGit2,
  Cpu
} from 'lucide-vue-next';

const emit = defineEmits<{
  (e: 'openSettings'): void;
  (e: 'openWorkspace'): void;
}>();

const store = useAgentStore();
const { sendPrompt, abortSession, createSession } = useSession();

const inputPrompt = ref('');
const messagesContainer = ref<HTMLDivElement | null>(null);
const textareaRef = ref<HTMLTextAreaElement | null>(null);

// Model Selector State
interface ProviderItem {
  providerId: string;
  name: string;
  models: string[];
}
const providers = ref<ProviderItem[]>([]);
const selectedModelKey = ref<string>(localStorage.getItem('bloom_selected_model') || '');

onMounted(async () => {
  await loadAvailableModels();
  // Listen for provider updates from SettingsModal
  window.addEventListener('bloom:providers-updated', loadAvailableModels);
});

async function loadAvailableModels() {
  try {
    const res = await fetch('/api/config/providers');
    if (res.ok) {
      providers.value = await res.json();
      // Auto select saved model or first available model
      const saved = localStorage.getItem('bloom_selected_model');
      if (saved && modelOptions.value.some(opt => opt.value === saved)) {
        selectedModelKey.value = saved;
      } else if (modelOptions.value.length > 0) {
        selectedModelKey.value = modelOptions.value[0].value;
        localStorage.setItem('bloom_selected_model', selectedModelKey.value);
      }
      if (store.currentSessionId && selectedModelKey.value) {
        handleModelChange();
      }
    }
  } catch (e) {
    console.warn('Failed loading providers for selector:', e);
  }
}

async function handleModelChange() {
  if (!selectedModelKey.value) return;
  localStorage.setItem('bloom_selected_model', selectedModelKey.value);
  if (store.currentSessionId && selectedModelKey.value.includes(':')) {
    const [provider, ...rest] = selectedModelKey.value.split(':');
    const modelId = rest.join(':');
    try {
      await fetch(`/api/sessions/${store.currentSessionId}/model`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ provider, id: modelId }),
      });
    } catch (e) {
      console.warn('Failed syncing model to session:', e);
    }
  }
}

const modelOptions = computed(() => {
  const options: { label: string; value: string; providerId: string; modelId: string }[] = [];
  providers.value.forEach(p => {
    (p.models || []).forEach(m => {
      options.push({
        label: `${p.name || p.providerId} / ${m}`,
        value: `${p.providerId}:${m}`,
        providerId: p.providerId,
        modelId: m,
      });
    });
  });
  return options;
});

let scrollRaf: number | null = null;
let userScrolledUp = false;

function onMessagesScroll() {
  if (!messagesContainer.value) return;
  const { scrollTop, scrollHeight, clientHeight } = messagesContainer.value;
  // If user scrolled up more than 60px from bottom, respect user's position
  userScrolledUp = scrollHeight - (scrollTop + clientHeight) > 60;
}

function scrollToBottom(force = false) {
  if (userScrolledUp && !force) return;
  if (scrollRaf !== null) return;
  scrollRaf = requestAnimationFrame(() => {
    scrollRaf = null;
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
    }
  });
}

watch(() => [store.transcript.length, store.streamingText, store.activeThinking], () => {
  scrollToBottom();
});

const isRunning = computed(() => store.currentPhase !== 'idle');

async function handleSendOrAbort() {
  if (isRunning.value) {
    await abortSession();
    return;
  }

  const prompt = inputPrompt.value.trim();
  if (!prompt) return;

  inputPrompt.value = '';
  adjustTextareaHeight();
  await sendPrompt(store.currentSessionId, prompt);
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    handleSendOrAbort();
  }
}

function adjustTextareaHeight() {
  nextTick(() => {
    if (textareaRef.value) {
      textareaRef.value.style.height = 'auto';
      textareaRef.value.style.height = `${Math.min(textareaRef.value.scrollHeight, 180)}px`;
    }
  });
}

function applyQuickPrompt(promptText: string) {
  inputPrompt.value = promptText;
  adjustTextareaHeight();
  handleSendOrAbort();
}
</script>

<template>
  <div class="flex flex-col h-full bg-gray-950 text-gray-100">
    <!-- Top Header Bar -->
    <header class="h-14 px-6 border-b border-gray-800 bg-gray-900/60 backdrop-blur flex items-center justify-between shrink-0">
      <div class="flex items-center gap-3 min-w-0">
        <h1 class="text-sm font-semibold text-gray-200 truncate">
          {{ store.currentSession?.name || store.currentSessionId || '未选择会话' }}
        </h1>
        <div class="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-medium"
             :class="isRunning ? 'bg-amber-500/10 text-amber-400 border border-amber-500/30' : 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'">
          <span class="w-1.5 h-1.5 rounded-full" :class="isRunning ? 'bg-amber-400 animate-ping' : 'bg-emerald-400'"></span>
          <span>{{ isRunning ? 'Agent 正在执行...' : '就绪' }}</span>
        </div>
      </div>

      <div class="flex items-center gap-4 text-xs text-gray-400">
        <button 
          v-if="store.currentSession?.cwd" 
          @click="emit('openWorkspace')"
          title="点击更换当前工作区目录"
          class="hidden sm:flex items-center gap-1.5 text-gray-400 hover:text-purple-300 px-2 py-1 rounded-lg hover:bg-gray-800 transition"
        >
          <FolderGit2 class="w-3.5 h-3.5 text-purple-400" />
          <span class="max-w-[200px] truncate font-mono text-[11px]">{{ store.currentSession.cwd }}</span>
        </button>
      </div>
    </header>

    <!-- Error Banner -->
    <div v-if="store.errorMessage" class="px-6 py-2 bg-rose-950/80 border-b border-rose-800 text-rose-200 text-xs flex items-center justify-between animate-fadeIn">
      <div class="flex items-center gap-2">
        <AlertTriangle class="w-4 h-4 text-rose-400 shrink-0" />
        <span>{{ store.errorMessage }}</span>
      </div>
      <button @click="store.setError(null)" class="text-rose-300 hover:text-white text-xs underline ml-2">关闭</button>
    </div>

    <!-- Messages Scroll Area -->
    <div ref="messagesContainer" class="flex-1 overflow-y-auto px-6 py-6 space-y-4">
      <!-- Empty State -->
      <div v-if="store.transcript.length === 0" class="h-full flex flex-col items-center justify-center text-center max-w-lg mx-auto py-12 space-y-6">
        <div class="w-16 h-16 rounded-2xl bg-purple-900/30 border border-purple-500/30 flex items-center justify-center shadow-lg shadow-purple-900/20">
          <Sparkles class="w-8 h-8 text-purple-400" />
        </div>
        <div>
          <h2 class="text-lg font-bold text-gray-200">BloomHarness AI 编码助手</h2>
          <p class="text-sm text-gray-400 mt-1">
            由 Java 21 与 Spring Boot 驱动的自主编码代理，已连接到本地工作区与工具链。
          </p>
        </div>

        <!-- Quick Start Cards -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-2.5 w-full text-left text-xs">
          <button 
            @click="applyQuickPrompt('检查当前项目目录结构并给出概要说明')"
            class="p-3 rounded-xl bg-gray-900/80 border border-gray-800 hover:border-purple-500/50 hover:bg-gray-800/80 transition text-gray-300 flex flex-col gap-1"
          >
            <span class="font-semibold text-purple-300">📁 检查项目结构</span>
            <span class="text-gray-500">自动列出主要代码模块与配置文件</span>
          </button>
          <button 
            @click="applyQuickPrompt('执行测试并分析是否有报错')"
            class="p-3 rounded-xl bg-gray-900/80 border border-gray-800 hover:border-purple-500/50 hover:bg-gray-800/80 transition text-gray-300 flex flex-col gap-1"
          >
            <span class="font-semibold text-purple-300">🧪 执行单元测试</span>
            <span class="text-gray-500">运行测试套件并汇报通过率</span>
          </button>
        </div>
      </div>

      <!-- Transcript Messages -->
      <template v-else>
        <MessageBubble v-for="msg in store.transcript" :key="msg.id" :message="msg" />

        <!-- Streaming Real-time Preview -->
        <div v-if="store.streamingText || store.activeThinking" class="flex gap-3 my-4 animate-fadeIn">
          <div class="w-8 h-8 rounded-full bg-purple-900/60 border border-purple-500/30 flex items-center justify-center shrink-0">
            <Loader2 class="w-4 h-4 text-purple-300 animate-spin" />
          </div>
          <div class="max-w-[85%] rounded-2xl rounded-bl-sm px-4 py-3 bg-gray-900 border border-purple-500/40 text-gray-200 shadow-lg">
            <div v-if="store.activeThinking" class="text-xs text-purple-300 font-mono mb-2 whitespace-pre-wrap opacity-90 border-b border-gray-800 pb-2">
              💭 {{ store.activeThinking }}
            </div>
            <div class="whitespace-pre-wrap leading-relaxed text-sm">{{ store.streamingText }}</div>
          </div>
        </div>
      </template>
    </div>

    <!-- Input Bar -->
    <div class="p-4 border-t border-gray-800 bg-gray-900/70 backdrop-blur shrink-0">
      <div class="max-w-4xl mx-auto">
        <div class="relative bg-gray-950 border border-gray-800 rounded-2xl focus-within:border-purple-500 focus-within:ring-1 focus-within:ring-purple-500 transition shadow-inner">
          <textarea 
            ref="textareaRef"
            v-model="inputPrompt"
            @input="adjustTextareaHeight"
            @keydown="handleKeydown"
            rows="1"
            placeholder="输入自然语言指令（如：构建项目、重构某函数... Enter 发送，Shift + Enter 换行）"
            :disabled="isRunning"
            class="w-full bg-transparent resize-none px-4 pt-3.5 pb-12 text-sm text-gray-200 placeholder-gray-500 focus:outline-none disabled:opacity-50 max-h-[180px]"
          ></textarea>

          <!-- Input Action Buttons (Model Selector + Send / Abort) -->
          <div class="absolute bottom-2.5 right-3 flex items-center gap-2">
            <!-- Provider & Model Dropdown Selector -->
            <div class="relative flex items-center">
              <div class="flex items-center gap-1.5 bg-gray-900/90 border border-gray-800 hover:border-gray-700 px-2.5 py-1.5 rounded-xl transition text-xs">
                <Cpu class="w-3.5 h-3.5 text-purple-400 shrink-0" />
                <select
                  v-model="selectedModelKey"
                  @change="handleModelChange"
                  :disabled="isRunning"
                  class="bg-transparent text-xs text-gray-300 focus:outline-none cursor-pointer max-w-[180px] sm:max-w-[220px] truncate font-mono"
                >
                  <option v-if="modelOptions.length === 0" value="" class="bg-gray-900 text-gray-500">未配置模型 (请点击⚙️添加)</option>
                  <option 
                    v-for="opt in modelOptions" 
                    :key="opt.value" 
                    :value="opt.value"
                    class="bg-gray-900 text-gray-200"
                  >
                    {{ opt.label }}
                  </option>
                </select>
              </div>
            </div>

            <button 
              @click="handleSendOrAbort"
              :disabled="!isRunning && !inputPrompt.trim()"
              class="px-4 py-2 rounded-xl font-medium text-xs flex items-center gap-1.5 transition shadow-md disabled:opacity-40 disabled:cursor-not-allowed"
              :class="isRunning 
                ? 'bg-rose-600 hover:bg-rose-500 active:bg-rose-700 text-white shadow-rose-950/50' 
                : 'bg-purple-600 hover:bg-purple-500 active:bg-purple-700 text-white shadow-purple-950/50'"
            >
              <template v-if="isRunning">
                <Square class="w-3.5 h-3.5 fill-current" />
                <span>停止生成</span>
              </template>
              <template v-else>
                <Send class="w-3.5 h-3.5" />
                <span>发送</span>
              </template>
            </button>
          </div>
        </div>
        <!-- Bottom Status Bar: Current Workspace & Send Shortcut -->
        <div class="flex justify-between items-center px-2 pt-2 text-[11px] text-gray-500">
          <div class="flex items-center gap-1.5 min-w-0 max-w-[70%] group">
            <FolderGit2 class="w-3.5 h-3.5 text-purple-400 shrink-0" />
            <span class="text-gray-400 font-mono truncate" :title="store.currentSession?.cwd || '当前根目录'">
              工作区: <span class="text-gray-300 font-medium select-all">{{ store.currentSession?.cwd || '当前根工作目录' }}</span>
            </span>
          </div>
          <div class="flex items-center gap-2 shrink-0">
            <span>按 <kbd class="px-1.5 py-0.5 bg-gray-800 border border-gray-700 rounded text-gray-300 font-mono">Enter</kbd> 发送</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
