<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import { 
  Folder, 
  FolderOpen, 
  ChevronRight, 
  ArrowUp, 
  RotateCw, 
  Check, 
  AlertCircle, 
  History, 
  Home, 
  Plus, 
  X,
  Compass,
  FolderSearch,
  HardDrive,
  Loader2
} from 'lucide-vue-next';

const props = defineProps<{
  currentCwd?: string;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'selectWorkspace', path: string, createNewSession: boolean): void;
}>();

const pathInput = ref(props.currentCwd || '');
const isValidating = ref(false);
const validationError = ref<string | null>(null);
const validationSuccess = ref(false);

const isBrowsing = ref(false);
const currentBrowsePath = ref('');
const parentBrowsePath = ref('');
const directoryList = ref<{ name: string; path: string; isReadable: boolean; isWritable: boolean }[]>([]);

const defaultWorkspace = ref('');
const userHome = ref('');
const recentWorkspaces = ref<string[]>([]);
const isPickingSystemFolder = ref(false);

onMounted(async () => {
  await loadCurrentInfo();
  await loadRecentWorkspaces();
  if (pathInput.value) {
    await validateAndBrowse(pathInput.value, false);
  } else if (defaultWorkspace.value) {
    pathInput.value = defaultWorkspace.value;
    await validateAndBrowse(defaultWorkspace.value, false);
  }
});

watch(() => props.currentCwd, (newVal) => {
  if (newVal) {
    pathInput.value = newVal;
    validateAndBrowse(newVal, false);
  }
});

async function loadCurrentInfo() {
  try {
    const res = await fetch('/api/workspace/current');
    if (res.ok) {
      const data = await res.json();
      defaultWorkspace.value = data.defaultWorkspace || '';
      userHome.value = data.userHome || '';
      if (!pathInput.value && defaultWorkspace.value) {
        pathInput.value = defaultWorkspace.value;
      }
    }
  } catch (e) {
    console.warn('Failed loading workspace current info:', e);
  }
}

async function loadRecentWorkspaces() {
  try {
    const res = await fetch('/api/workspace/recent');
    if (res.ok) {
      recentWorkspaces.value = await res.json();
    }
  } catch (e) {
    console.warn('Failed loading recent workspaces:', e);
  }
}

async function validatePath(pathStr: string): Promise<boolean> {
  if (!pathStr || !pathStr.trim()) {
    validationError.value = '请输入或选择有效的工作区路径';
    validationSuccess.value = false;
    return false;
  }

  isValidating.value = true;
  validationError.value = null;
  validationSuccess.value = false;

  try {
    const res = await fetch('/api/workspace/validate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ path: pathStr.trim() }),
    });
    if (res.ok) {
      const data = await res.json();
      if (data.valid) {
        pathInput.value = data.canonicalPath;
        validationSuccess.value = true;
        validationError.value = null;
        return true;
      } else {
        validationError.value = data.error || '无效的目录路径';
        validationSuccess.value = false;
        return false;
      }
    } else {
      validationError.value = `服务器校验失败 (HTTP ${res.status})`;
      return false;
    }
  } catch (e: any) {
    validationError.value = `请求异常: ${e.message || e}`;
    return false;
  } finally {
    isValidating.value = false;
  }
}

async function browseDirectory(targetPath?: string) {
  isBrowsing.value = true;
  try {
    const url = targetPath ? `/api/workspace/browse?path=${encodeURIComponent(targetPath)}` : '/api/workspace/browse';
    const res = await fetch(url);
    if (res.ok) {
      const data = await res.json();
      currentBrowsePath.value = data.currentPath || '';
      parentBrowsePath.value = data.parentPath || '';
      directoryList.value = data.directories || [];
    }
  } catch (e) {
    console.warn('Failed browsing directory:', e);
  } finally {
    isBrowsing.value = false;
  }
}

async function validateAndBrowse(path: string, doBrowse = true) {
  pathInput.value = path;
  const ok = await validatePath(path);
  if (ok && doBrowse) {
    await browseDirectory(path);
  } else if (!currentBrowsePath.value) {
    await browseDirectory(path);
  }
}

function handleSelectDirectory(itemPath: string) {
  pathInput.value = itemPath;
  validateAndBrowse(itemPath, true);
}

function handleGoParent() {
  if (parentBrowsePath.value) {
    handleSelectDirectory(parentBrowsePath.value);
  }
}

async function handlePickSystemFolder() {
  isPickingSystemFolder.value = true;
  validationError.value = null;
  try {
    const res = await fetch('/api/workspace/pick-folder', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ initialPath: pathInput.value || defaultWorkspace.value }),
    });
    if (res.ok) {
      const data = await res.json();
      if (data.success && data.path) {
        await validateAndBrowse(data.path, true);
      } else if (data.message) {
        // Only set error if not cancelled
        if (!data.message.includes('已关闭')) {
          validationError.value = data.message;
        }
      }
    }
  } catch (e: any) {
    validationError.value = `启动系统文件夹选择器失败: ${e.message || e}`;
  } finally {
    isPickingSystemFolder.value = false;
  }
}

async function handleConfirm(createNewSession: boolean) {
  const ok = await validatePath(pathInput.value);
  if (ok) {
    emit('selectWorkspace', pathInput.value.trim(), createNewSession);
    emit('close');
  }
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4 animate-fadeIn">
    <div class="w-full max-w-2xl bg-gray-900 border border-gray-800 rounded-2xl shadow-2xl flex flex-col max-h-[90vh] overflow-hidden">
      
      <!-- Header -->
      <div class="h-14 px-6 border-b border-gray-800 flex items-center justify-between shrink-0 bg-gray-900/80">
        <div class="flex items-center gap-2.5">
          <div class="w-8 h-8 rounded-xl bg-purple-600/20 border border-purple-500/30 flex items-center justify-center text-purple-400">
            <Compass class="w-4 h-4" />
          </div>
          <div>
            <h2 class="text-sm font-semibold text-gray-200">选择工作区 (Workspace)</h2>
            <p class="text-[11px] text-gray-500">指定 AI 编码助手执行文件读取、编辑和终端命令的工作目录</p>
          </div>
        </div>
        <button 
          @click="emit('close')"
          class="p-1.5 rounded-lg text-gray-400 hover:text-gray-200 hover:bg-gray-800 transition"
        >
          <X class="w-4 h-4" />
        </button>
      </div>

      <!-- Content Body -->
      <div class="p-6 overflow-y-auto space-y-5 flex-1 text-xs">
        
        <!-- Path Input Box -->
        <div class="space-y-1.5">
          <div class="flex items-center justify-between">
            <label class="block font-medium text-gray-300">当前工作区路径</label>
            <button
              @click="handlePickSystemFolder"
              :disabled="isPickingSystemFolder"
              class="text-purple-400 hover:text-purple-300 flex items-center gap-1 text-[11px] font-medium transition disabled:opacity-50"
              title="调用操作系统原生文件管理器选择文件夹"
            >
              <Loader2 v-if="isPickingSystemFolder" class="w-3.5 h-3.5 animate-spin" />
              <FolderSearch v-else class="w-3.5 h-3.5" />
              <span>{{ isPickingSystemFolder ? '正在等待系统选择...' : '打开系统文件夹选择器' }}</span>
            </button>
          </div>

          <div class="flex items-center gap-2">
            <div class="relative flex-1">
              <input 
                v-model="pathInput"
                type="text"
                placeholder="例如: /home/clau/codes/my-project"
                @keydown.enter="validateAndBrowse(pathInput, true)"
                class="w-full bg-gray-950 border border-gray-800 rounded-xl px-3 py-2.5 font-mono text-xs text-gray-200 placeholder-gray-600 focus:outline-none focus:border-purple-500 transition"
              />
              <div v-if="validationSuccess" class="absolute right-3 top-2.5 text-emerald-400 flex items-center gap-1">
                <Check class="w-3.5 h-3.5" />
                <span class="text-[10px]">有效</span>
              </div>
            </div>

            <!-- System Folder Selection Button -->
            <button 
              @click="handlePickSystemFolder"
              :disabled="isPickingSystemFolder"
              class="px-3.5 py-2.5 rounded-xl bg-purple-600/20 border border-purple-500/40 hover:bg-purple-600/30 text-purple-300 font-medium transition flex items-center gap-1.5 shrink-0 shadow-sm disabled:opacity-50"
              title="调用系统原生窗口选择本地文件夹"
            >
              <Loader2 v-if="isPickingSystemFolder" class="w-3.5 h-3.5 animate-spin" />
              <FolderSearch v-else class="w-3.5 h-3.5 text-purple-400" />
              <span>选择系统文件夹</span>
            </button>

            <button 
              @click="validateAndBrowse(pathInput, true)"
              :disabled="isValidating"
              class="px-3.5 py-2.5 rounded-xl bg-gray-800 hover:bg-gray-700 text-gray-200 font-medium transition flex items-center gap-1.5 shrink-0"
            >
              <RotateCw v-if="isValidating" class="w-3.5 h-3.5 animate-spin" />
              <span>验证并浏览</span>
            </button>
          </div>

          <div v-if="validationError" class="flex items-center gap-1 text-rose-400 text-[11px] pt-1">
            <AlertCircle class="w-3.5 h-3.5 shrink-0" />
            <span>{{ validationError }}</span>
          </div>
        </div>

        <!-- Quick Locations / Recent -->
        <div class="space-y-2">
          <div class="text-[11px] font-medium text-gray-400 flex items-center gap-1.5">
            <History class="w-3.5 h-3.5 text-purple-400" />
            <span>快捷与常用工作区</span>
          </div>
          <div class="flex flex-wrap gap-2">
            <button 
              v-if="defaultWorkspace"
              @click="validateAndBrowse(defaultWorkspace, true)"
              class="px-2.5 py-1 rounded-lg bg-gray-800/80 hover:bg-purple-900/30 border border-gray-700/60 hover:border-purple-500/50 text-gray-300 font-mono text-[11px] transition flex items-center gap-1.5"
            >
              <Home class="w-3 h-3 text-purple-400" />
              <span>系统默认 ({{ defaultWorkspace.split('/').pop() }})</span>
            </button>

            <button 
              v-for="rec in recentWorkspaces" 
              :key="rec"
              v-show="rec !== defaultWorkspace"
              @click="validateAndBrowse(rec, true)"
              class="px-2.5 py-1 rounded-lg bg-gray-800/80 hover:bg-purple-900/30 border border-gray-700/60 hover:border-purple-500/50 text-gray-300 font-mono text-[11px] transition flex items-center gap-1.5 truncate max-w-xs"
              :title="rec"
            >
              <Folder class="w-3 h-3 text-gray-400" />
              <span class="truncate">{{ rec }}</span>
            </button>
          </div>
        </div>

        <!-- Directory Browser Tree -->
        <div class="space-y-2">
          <div class="flex items-center justify-between text-[11px] text-gray-400">
            <div class="flex items-center gap-1.5">
              <FolderOpen class="w-3.5 h-3.5 text-purple-400" />
              <span class="font-medium">目录文件管理器</span>
            </div>
            <span class="font-mono text-[10px] text-gray-500 truncate max-w-xs">{{ currentBrowsePath }}</span>
          </div>

          <div class="bg-gray-950 border border-gray-800 rounded-xl p-2 max-h-56 overflow-y-auto space-y-1">
            <!-- Go to parent dir -->
            <button 
              v-if="parentBrowsePath"
              @click="handleGoParent"
              class="w-full px-2.5 py-1.5 rounded-lg hover:bg-gray-900 text-left text-gray-400 hover:text-purple-300 flex items-center gap-2 transition"
            >
              <ArrowUp class="w-3.5 h-3.5 text-purple-400" />
              <span class="font-mono">.. (返回上一级: {{ parentBrowsePath }})</span>
            </button>

            <div v-if="directoryList.length === 0" class="py-6 text-center text-gray-600 text-xs">
              该目录下无子文件夹
            </div>

            <!-- Directory Items -->
            <div 
              v-for="dir in directoryList" 
              :key="dir.path"
              @click="handleSelectDirectory(dir.path)"
              class="group px-2.5 py-1.5 rounded-lg hover:bg-gray-900 cursor-pointer flex items-center justify-between transition"
              :class="pathInput === dir.path ? 'bg-purple-950/40 border border-purple-800/40 text-purple-200' : 'text-gray-300'"
            >
              <div class="flex items-center gap-2 truncate">
                <Folder class="w-3.5 h-3.5 text-indigo-400 group-hover:text-purple-400 shrink-0" />
                <span class="truncate font-mono">{{ dir.name }}</span>
              </div>
              <ChevronRight class="w-3 h-3 text-gray-600 group-hover:text-gray-400 shrink-0" />
            </div>
          </div>
        </div>

      </div>

      <!-- Footer Actions -->
      <div class="p-4 px-6 border-t border-gray-800 bg-gray-900/90 flex items-center justify-between shrink-0">
        <div class="text-[11px] text-gray-500 font-mono truncate max-w-[280px]">
          已选: <span class="text-purple-300">{{ pathInput || '未选择' }}</span>
        </div>

        <div class="flex items-center gap-2">
          <button 
            @click="emit('close')"
            class="px-3 py-2 rounded-xl text-xs font-medium text-gray-400 hover:text-gray-200 hover:bg-gray-800 transition"
          >
            取消
          </button>

          <button 
            @click="handleConfirm(true)"
            title="使用该工作区创建新会话"
            class="px-3.5 py-2 rounded-xl text-xs font-medium bg-gray-800 hover:bg-gray-700 text-gray-200 transition flex items-center gap-1.5 shadow-sm"
          >
            <Plus class="w-3.5 h-3.5 text-purple-400" />
            <span>新建会话</span>
          </button>

          <button 
            @click="handleConfirm(false)"
            title="应用到当前会话工作区"
            class="px-4 py-2 rounded-xl text-xs font-medium bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 text-white transition flex items-center gap-1.5 shadow-md shadow-purple-900/30"
          >
            <Check class="w-3.5 h-3.5" />
            <span>应用至当前会话</span>
          </button>
        </div>
      </div>

    </div>
  </div>
</template>

<style scoped>
@keyframes fadeIn {
  from { opacity: 0; transform: scale(0.98); }
  to { opacity: 1; transform: scale(1); }
}
.animate-fadeIn {
  animation: fadeIn 0.15s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}
</style>
