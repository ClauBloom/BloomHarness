<script setup lang="ts">
import { onMounted } from 'vue';
import { useAgentStore } from '@/stores/agentStore';
import { useSession } from '@/composables/useSession';
import { Plus, MessageSquare, Bot, Folder, Loader2, RotateCw, Settings } from 'lucide-vue-next';

const emit = defineEmits<{
  (e: 'openSettings'): void;
  (e: 'openWorkspace'): void;
}>();

const store = useAgentStore();
const { fetchSessions, createSession, selectSession } = useSession();

onMounted(async () => {
  const list = await fetchSessions();
  if (list && list.length > 0) {
    if (!store.currentSessionId) {
      await selectSession(list[0].id);
    }
  } else {
    // 首次进入无会话时，自动创建一个初始会话，保障开箱即可使用
    console.log('No existing sessions found. Auto-creating initial session...');
    await createSession('默认工作会话');
  }
});

async function handleCreateNew() {
  if (store.isCreatingSession) return;
  await createSession();
}

async function handleRefresh() {
  await fetchSessions();
}
</script>

<template>
  <div class="w-64 h-full bg-gray-900/90 border-r border-gray-800 flex flex-col shrink-0 select-none">
    <!-- Brand Header -->
    <div class="h-14 px-4 border-b border-gray-800 flex items-center justify-between">
      <div class="flex items-center gap-2.5">
        <div class="w-7 h-7 rounded-lg bg-gradient-to-tr from-purple-600 to-indigo-500 flex items-center justify-center shadow-md shadow-purple-900/30">
          <Bot class="w-4 h-4 text-white" />
        </div>
        <div class="flex flex-col">
          <span class="font-bold text-sm bg-gradient-to-r from-purple-300 via-indigo-200 to-purple-200 bg-clip-text text-transparent">
            BloomHarness
          </span>
          <span class="text-[10px] text-gray-500 font-mono -mt-0.5">AI Coding Agent</span>
        </div>
      </div>

      <div class="flex items-center gap-1">
        <button 
          @click="emit('openWorkspace')"
          title="切换工作区"
          class="p-1.5 rounded-lg text-gray-400 hover:text-purple-300 hover:bg-gray-800 transition"
        >
          <Folder class="w-3.5 h-3.5" />
        </button>

        <button 
          @click="emit('openSettings')"
          title="模型与提供商设置"
          class="p-1.5 rounded-lg text-gray-400 hover:text-gray-200 hover:bg-gray-800 transition"
        >
          <Settings class="w-3.5 h-3.5" />
        </button>

        <button 
          @click="handleRefresh"
          title="刷新会话列表"
          class="p-1.5 rounded-lg text-gray-400 hover:text-gray-200 hover:bg-gray-800 transition"
        >
          <RotateCw class="w-3.5 h-3.5" />
        </button>

        <button 
          @click="handleCreateNew" 
          :disabled="store.isCreatingSession"
          title="新建会话"
          class="p-1.5 rounded-lg bg-purple-600/80 hover:bg-purple-600 text-white transition disabled:opacity-50 flex items-center justify-center shadow-sm"
        >
          <Loader2 v-if="store.isCreatingSession" class="w-3.5 h-3.5 animate-spin" />
          <Plus v-else class="w-3.5 h-3.5" />
        </button>
      </div>
    </div>

    <!-- Session List -->
    <div class="flex-1 overflow-y-auto p-2 space-y-1">
      <div v-if="store.sessionList.length === 0" class="h-40 flex flex-col items-center justify-center text-center p-4">
        <Loader2 v-if="store.isCreatingSession" class="w-5 h-5 text-purple-400 animate-spin mb-2" />
        <p class="text-xs text-gray-500">
          {{ store.isCreatingSession ? '正在初始化会话...' : '暂无会话，请点击右上角 +' }}
        </p>
      </div>

      <div 
        v-for="s in store.sessionList" 
        :key="s.id"
        @click="selectSession(s.id)"
        class="group p-2.5 rounded-xl cursor-pointer flex items-center gap-2.5 text-xs transition border"
        :class="store.currentSessionId === s.id 
          ? 'bg-purple-950/50 border-purple-800/50 text-purple-200 shadow-sm' 
          : 'border-transparent hover:bg-gray-800/60 text-gray-400 hover:text-gray-200'"
      >
        <MessageSquare class="w-4 h-4 shrink-0" :class="store.currentSessionId === s.id ? 'text-purple-400' : 'text-gray-500'" />
        <div class="flex-1 truncate font-medium">
          {{ s.sessionName || s.id }}
        </div>
      </div>
    </div>

    <!-- Footer CWD & Status -->
    <div class="p-3 border-t border-gray-800 bg-gray-950/50 text-[11px] text-gray-400 flex flex-col gap-1">
      <div 
        @click="emit('openWorkspace')"
        class="flex items-center justify-between cursor-pointer group hover:text-purple-300 transition"
        title="点击选择/切换工作区"
      >
        <div class="flex items-center gap-1.5 truncate">
          <Folder class="w-3.5 h-3.5 text-gray-500 group-hover:text-purple-400 shrink-0" />
          <span class="truncate font-mono" :title="store.currentSession?.cwd || '默认工作区'">
            {{ store.currentSession?.cwd ? store.currentSession.cwd.split('/').filter(Boolean).pop() : '工作区' }}
          </span>
        </div>
        <span class="text-[10px] text-purple-400/80 group-hover:text-purple-300 font-sans shrink-0">切换</span>
      </div>
      <div class="flex items-center justify-between text-[10px] text-gray-500 pt-0.5">
        <span>连接状态</span>
        <span class="flex items-center gap-1 text-emerald-400">
          <span class="w-1.5 h-1.5 rounded-full bg-emerald-400"></span>
          在线
        </span>
      </div>
    </div>
  </div>
</template>
