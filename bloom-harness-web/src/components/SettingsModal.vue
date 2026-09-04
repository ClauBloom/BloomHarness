<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { 
  X, 
  Settings, 
  Key, 
  Globe, 
  Cpu, 
  Save, 
  Eye, 
  EyeOff, 
  Check, 
  AlertCircle, 
  Loader2,
  Plus,
  Trash2,
  Sparkles,
  Bot,
  Zap,
  CheckCircle2,
  XCircle
} from 'lucide-vue-next';

const emit = defineEmits<{ (e: 'close'): void }>();

interface Provider {
  providerId: string;
  name: string;
  baseUrl: string;
  apiKey: string;
  protocol: string;
  models: string[];
  isConfigured?: boolean;
}

// Providers State
const providers = ref<Provider[]>([]);
const selectedProviderId = ref<string>('');
const isLoading = ref(false);
const isSaving = ref(false);
const isTesting = ref(false);
const isFetchingModels = ref(false);
const testResult = ref<{ ok: boolean; latencyMs?: number; message: string } | null>(null);
const statusMessage = ref<{ type: 'success' | 'error'; text: string } | null>(null);
const showApiKey = ref(false);

// New Model Input State
const newModelInput = ref('');

const currentProvider = ref<Provider>({
  providerId: '',
  name: '',
  baseUrl: '',
  apiKey: '',
  protocol: 'openai',
  models: [],
});

onMounted(async () => {
  await loadProviders();
});

async function loadProviders() {
  isLoading.value = true;
  try {
    const res = await fetch('/api/config/providers?reveal=false');
    if (res.ok) {
      const data = await res.json();
      providers.value = data;
      if (providers.value.length > 0 && !selectedProviderId.value) {
        selectProvider(providers.value[0].providerId);
      }
    }
  } catch (e: any) {
    console.error('Failed to load providers:', e);
    showMessage('error', '加载供应商配置失败');
  } finally {
    isLoading.value = false;
  }
}

function selectProvider(id: string) {
  selectedProviderId.value = id;
  testResult.value = null;
  const p = providers.value.find(item => item.providerId === id);
  if (p) {
    currentProvider.value = { 
      ...p, 
      models: [...(p.models || [])] 
    };
  }
  showApiKey.value = false;
}

function handleAddCustomProvider() {
  const newId = `custom-${Date.now().toString().slice(-4)}`;
  const newP: Provider = {
    providerId: newId,
    name: '新建自定义服务商',
    baseUrl: '',
    apiKey: '',
    protocol: 'openai',
    models: [],
    isConfigured: false,
  };
  providers.value.push(newP);
  selectProvider(newId);
}

function handleAddModel() {
  const m = newModelInput.value.trim();
  if (!m) return;
  if (!currentProvider.value.models) {
    currentProvider.value.models = [];
  }
  if (!currentProvider.value.models.includes(m)) {
    currentProvider.value.models.push(m);
  }
  newModelInput.value = '';
}

function handleRemoveModel(index: number) {
  currentProvider.value.models.splice(index, 1);
}

// Test Provider Connection
async function handleTestConnection() {
  if (!currentProvider.value.baseUrl) {
    showMessage('error', '请先填写 Base URL');
    return;
  }
  isTesting.value = true;
  testResult.value = null;

  try {
    const res = await fetch('/api/config/providers/test', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        providerId: currentProvider.value.providerId,
        baseUrl: currentProvider.value.baseUrl,
        apiKey: currentProvider.value.apiKey,
      }),
    });
    if (res.ok) {
      const result = await res.json();
      testResult.value = result;
    } else {
      testResult.value = { ok: false, message: `测试接口异常 HTTP ${res.status}` };
    }
  } catch (e: any) {
    testResult.value = { ok: false, message: `连接超时或网络异常: ${e.message || e}` };
  } finally {
    isTesting.value = false;
  }
}

// Fetch Model List from Upstream Provider
async function handleFetchModels() {
  if (!currentProvider.value.baseUrl) {
    showMessage('error', '请先填写 Base URL 才能获取模型列表');
    return;
  }
  isFetchingModels.value = true;

  try {
    const res = await fetch('/api/config/providers/fetch-models', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        providerId: currentProvider.value.providerId,
        baseUrl: currentProvider.value.baseUrl,
        apiKey: currentProvider.value.apiKey,
      }),
    });

    if (res.ok) {
      const data = await res.json();
      if (data.ok && Array.isArray(data.models) && data.models.length > 0) {
        // Merge without duplicates
        const existing = new Set(currentProvider.value.models || []);
        let addedCount = 0;
        for (const m of data.models) {
          if (!existing.has(m)) {
            currentProvider.value.models.push(m);
            existing.add(m);
            addedCount++;
          }
        }
        showMessage('success', `成功获取并同步 ${data.models.length} 个模型 (新增 ${addedCount} 个)`);
      } else {
        showMessage('error', data.message || '未能拉取到模型列表');
      }
    } else {
      showMessage('error', `拉取模型接口异常 HTTP ${res.status}`);
    }
  } catch (e: any) {
    showMessage('error', `拉取模型失败: ${e.message || e}`);
  } finally {
    isFetchingModels.value = false;
  }
}

async function handleSaveProvider() {
  isSaving.value = true;
  statusMessage.value = null;

  try {
    const res = await fetch('/api/config/providers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(currentProvider.value),
    });

    if (res.ok) {
      showMessage('success', `${currentProvider.value.name} 配置已保存并同步！`);
      const idx = providers.value.findIndex(p => p.providerId === currentProvider.value.providerId);
      const isKeyConfigured = !!currentProvider.value.apiKey && currentProvider.value.apiKey.length > 0;
      if (idx >= 0) {
        providers.value[idx] = { 
          ...currentProvider.value, 
          isConfigured: isKeyConfigured 
        };
      }
      // Broadcast event so ChatPanel and other components immediately refresh their model lists
      window.dispatchEvent(new CustomEvent('bloom:providers-updated'));
    } else {
      const err = await res.json();
      showMessage('error', err.error || '保存失败');
    }
  } catch (e: any) {
    console.error('Failed saving provider:', e);
    showMessage('error', `保存异常: ${e.message || e}`);
  } finally {
    isSaving.value = false;
  }
}

async function handleDelete(providerId: string) {
  if (!confirm(`确定要移除 ${providerId} 供应商吗？`)) return;

  try {
    const res = await fetch(`/api/config/providers/${providerId}`, {
      method: 'DELETE',
    });
    if (res.ok) {
      providers.value = providers.value.filter(p => p.providerId !== providerId);
      if (providers.value.length > 0) {
        selectProvider(providers.value[0].providerId);
      }
      showMessage('success', '已删除供应商配置');
    }
  } catch (e: any) {
    showMessage('error', '删除失败');
  }
}

function showMessage(type: 'success' | 'error', text: string) {
  statusMessage.value = { type, text };
  setTimeout(() => {
    if (statusMessage.value?.text === text) {
      statusMessage.value = null;
    }
  }, 4000);
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4 animate-fadeIn">
    <div class="w-full max-w-3xl bg-zinc-900 border border-zinc-800 rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[85vh]">
      
      <!-- Top Header -->
      <div class="px-6 py-4 border-b border-zinc-800 flex items-center justify-between bg-zinc-950/80">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 rounded-xl bg-purple-950/80 border border-purple-500/30 flex items-center justify-center text-purple-400">
            <Settings class="w-4 h-4" />
          </div>
          <div>
            <h2 class="text-sm font-semibold text-zinc-100 flex items-center gap-2">
              <span>模型与服务商设置</span>
              <span class="text-[10px] font-mono px-1.5 py-0.5 rounded bg-zinc-800 text-zinc-400 border border-zinc-700">AI Router</span>
            </h2>
            <p class="text-[11px] text-zinc-500 font-mono">配置上游 BaseURL、API Key 并对接至 ai-router-core 路由引擎</p>
          </div>
        </div>

        <button 
          @click="emit('close')"
          class="p-1.5 rounded-lg text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 transition"
        >
          <X class="w-4 h-4" />
        </button>
      </div>

      <!-- Main Body: Provider Directory + Card Editor -->
      <div class="flex-1 flex overflow-hidden min-h-[440px]">
        <!-- Left: Provider Directory Sidebar -->
        <div class="w-56 border-r border-zinc-800 bg-zinc-950/40 p-3.5 flex flex-col justify-between shrink-0">
          <div class="space-y-1.5 overflow-y-auto">
            <div class="flex items-center justify-between text-[11px] font-semibold uppercase tracking-wider text-zinc-500 px-2 py-1">
              <span>服务商列表</span>
              <span class="text-[10px] font-mono text-zinc-600">{{ providers.length }}</span>
            </div>

            <!-- Empty state -->
            <div v-if="providers.length === 0" class="p-3 text-center text-xs text-zinc-600">
              暂无配置，请点击下方添加
            </div>

            <!-- Provider Rows with Status Dots -->
            <button
              v-for="p in providers"
              :key="p.providerId"
              @click="selectProvider(p.providerId)"
              class="w-full text-left px-3 py-2.5 rounded-xl text-xs font-medium flex items-center justify-between transition group border"
              :class="selectedProviderId === p.providerId 
                ? 'bg-purple-950/40 text-purple-200 border-purple-800/50' 
                : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900 border-transparent'"
            >
              <div class="flex items-center gap-2 truncate">
                <!-- Status Dot (🟢 Configured / ⚪ Empty Key) -->
                <span 
                  class="w-2 h-2 rounded-full shrink-0 transition-colors" 
                  :class="p.isConfigured ? 'bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.5)]' : 'bg-zinc-600'"
                  :title="p.isConfigured ? '凭证已配置' : '缺失 API Key'"
                ></span>
                <span class="truncate">{{ p.name || p.providerId }}</span>
              </div>

              <span class="text-[10px] font-mono text-zinc-600 group-hover:text-zinc-400">
                {{ (p.models || []).length }} 模型
              </span>
            </button>
          </div>

          <!-- Add Provider Action -->
          <button
            @click="handleAddCustomProvider"
            class="w-full py-2.5 px-3 rounded-xl border border-dashed border-zinc-700 hover:border-purple-500/80 hover:text-purple-300 text-zinc-400 text-xs flex items-center justify-center gap-1.5 transition bg-zinc-900/40 hover:bg-purple-950/20"
          >
            <Plus class="w-3.5 h-3.5" />
            <span>添加自定义服务商</span>
          </button>
        </div>

        <!-- Right: Provider Card Detail & Editor -->
        <div class="flex-1 p-6 overflow-y-auto space-y-4">
          <!-- Status Notification Banner -->
          <div 
            v-if="statusMessage"
            class="p-3 rounded-xl text-xs flex items-center gap-2 transition animate-fadeIn"
            :class="statusMessage.type === 'success' 
              ? 'bg-emerald-950/40 border border-emerald-800/50 text-emerald-300' 
              : 'bg-rose-950/40 border border-rose-800/50 text-rose-300'"
          >
            <Check v-if="statusMessage.type === 'success'" class="w-4 h-4 shrink-0 text-emerald-400" />
            <AlertCircle v-else class="w-4 h-4 shrink-0 text-rose-400" />
            <span>{{ statusMessage.text }}</span>
          </div>

          <div v-if="currentProvider.providerId" class="space-y-4">
            <!-- Provider Name & Wire Protocol -->
            <div class="grid grid-cols-3 gap-3.5">
              <div class="col-span-2">
                <label class="block text-xs font-medium text-zinc-400 mb-1.5">服务商名称 (Provider Name)</label>
                <input 
                  v-model="currentProvider.name"
                  type="text"
                  placeholder="例如: DeepSeek / 本地 Ollama / 私有模型"
                  class="w-full bg-zinc-950 border border-zinc-800 focus:border-purple-500 rounded-xl px-3.5 py-2 text-xs text-zinc-200 focus:outline-none transition"
                />
              </div>

              <div>
                <label class="block text-xs font-medium text-zinc-400 mb-1.5">通讯协议 (Wire Protocol)</label>
                <select 
                  v-model="currentProvider.protocol"
                  class="w-full bg-zinc-950 border border-zinc-800 focus:border-purple-500 rounded-xl px-3 py-2 text-xs text-zinc-200 focus:outline-none transition"
                >
                  <option value="openai">OpenAI Compatible</option>
                  <option value="anthropic">Anthropic Messages</option>
                  <option value="deepseek">DeepSeek Native</option>
                  <option value="ollama">Ollama</option>
                </select>
              </div>
            </div>

            <!-- Base URL -->
            <div>
              <label class="block text-xs font-medium text-zinc-400 mb-1.5 flex items-center gap-1.5">
                <Globe class="w-3.5 h-3.5 text-purple-400" />
                <span>Base URL (API 端点)</span>
              </label>
              <input 
                v-model="currentProvider.baseUrl"
                type="text"
                placeholder="https://api.deepseek.com/v1 或本地 http://localhost:11434"
                class="w-full bg-zinc-950 border border-zinc-800 focus:border-purple-500 rounded-xl px-3.5 py-2 text-xs font-mono text-zinc-200 focus:outline-none transition"
              />
            </div>

            <!-- API Key & Connection Probe -->
            <div class="bg-zinc-950/60 border border-zinc-800/80 rounded-xl p-3.5 space-y-3">
              <div class="flex items-center justify-between">
                <label class="text-xs font-medium text-zinc-300 flex items-center gap-1.5">
                  <Key class="w-3.5 h-3.5 text-purple-400" />
                  <span>API Key (机密凭证)</span>
                </label>

                <!-- Test Connection Button -->
                <button 
                  type="button"
                  @click="handleTestConnection"
                  :disabled="isTesting"
                  class="px-2.5 py-1 rounded-lg bg-zinc-800 hover:bg-zinc-700 text-zinc-300 text-[11px] font-medium flex items-center gap-1.5 transition disabled:opacity-50"
                >
                  <Loader2 v-if="isTesting" class="w-3 h-3 animate-spin text-purple-400" />
                  <Zap v-else class="w-3 h-3 text-amber-400" />
                  <span>{{ isTesting ? '正在探测...' : '测试连通性' }}</span>
                </button>
              </div>

              <div class="relative">
                <input 
                  v-model="currentProvider.apiKey"
                  :type="showApiKey ? 'text' : 'password'"
                  placeholder="sk-..."
                  class="w-full bg-zinc-900 border border-zinc-800 focus:border-purple-500 rounded-xl pl-3.5 pr-10 py-2 text-xs font-mono text-zinc-200 focus:outline-none transition"
                />
                <button 
                  type="button"
                  @click="showApiKey = !showApiKey"
                  class="absolute right-2.5 top-1/2 -translate-y-1/2 text-zinc-500 hover:text-zinc-300 p-1"
                >
                  <EyeOff v-if="showApiKey" class="w-3.5 h-3.5" />
                  <Eye v-else class="w-3.5 h-3.5" />
                </button>
              </div>

              <!-- Test Connection Feedback Result -->
              <div 
                v-if="testResult" 
                class="text-[11px] p-2 rounded-lg flex items-center gap-2"
                :class="testResult.ok ? 'bg-emerald-950/50 text-emerald-300 border border-emerald-800/40' : 'bg-rose-950/50 text-rose-300 border border-rose-800/40'"
              >
                <CheckCircle2 v-if="testResult.ok" class="w-3.5 h-3.5 shrink-0 text-emerald-400" />
                <XCircle v-else class="w-3.5 h-3.5 shrink-0 text-rose-400" />
                <span>{{ testResult.message }}</span>
              </div>
            </div>

            <!-- Model List Editor -->
            <div class="space-y-2.5">
              <div class="flex items-center justify-between">
                <label class="text-xs font-medium text-zinc-400 flex items-center gap-1.5">
                  <Cpu class="w-3.5 h-3.5 text-purple-400" />
                  <span>模型清单 (Model Registry)</span>
                </label>
                <div class="flex items-center gap-2">
                  <button
                    type="button"
                    @click="handleFetchModels"
                    :disabled="isFetchingModels || !currentProvider.baseUrl"
                    class="px-2.5 py-1 rounded-lg bg-purple-950/50 hover:bg-purple-900/60 border border-purple-800/50 text-purple-300 text-[11px] font-medium flex items-center gap-1.5 transition disabled:opacity-50"
                  >
                    <Loader2 v-if="isFetchingModels" class="w-3 h-3 animate-spin text-purple-300" />
                    <Sparkles v-else class="w-3 h-3 text-purple-400" />
                    <span>{{ isFetchingModels ? '正在拉取...' : '一键获取模型列表' }}</span>
                  </button>
                  <span class="text-[11px] text-zinc-500 font-mono">已注册 {{ (currentProvider.models || []).length }}</span>
                </div>
              </div>

              <!-- Input to add model -->
              <div class="flex items-center gap-2">
                <input 
                  v-model="newModelInput"
                  @keydown.enter.prevent="handleAddModel"
                  type="text"
                  placeholder="输入模型标识并回车 (如: deepseek-chat, qwen-2.5-coder)"
                  class="flex-1 bg-zinc-950 border border-zinc-800 focus:border-purple-500 rounded-xl px-3.5 py-2 text-xs font-mono text-zinc-200 focus:outline-none transition"
                />
                <button 
                  type="button"
                  @click="handleAddModel"
                  class="px-3.5 py-2 rounded-xl bg-zinc-800 hover:bg-zinc-700 text-zinc-200 text-xs font-medium flex items-center gap-1 transition shrink-0"
                >
                  <Plus class="w-3.5 h-3.5" />
                  <span>添加</span>
                </button>
              </div>

              <!-- Models Badges / List -->
              <div class="flex flex-wrap gap-2 pt-1 max-h-36 overflow-y-auto">
                <div 
                  v-for="(model, idx) in currentProvider.models" 
                  :key="model"
                  class="bg-zinc-950 border border-zinc-800 rounded-lg px-2.5 py-1.5 text-xs font-mono text-zinc-300 flex items-center gap-2 group hover:border-zinc-700"
                >
                  <Sparkles class="w-3 h-3 text-purple-400" />
                  <span>{{ model }}</span>
                  <button 
                    type="button" 
                    @click="handleRemoveModel(idx)"
                    class="text-zinc-600 hover:text-rose-400 transition"
                    title="移除模型"
                  >
                    <X class="w-3 h-3" />
                  </button>
                </div>
                <div v-if="!currentProvider.models || currentProvider.models.length === 0" class="text-xs text-zinc-600 py-1">
                  暂未添加模型，请在上框输入后回车添加
                </div>
              </div>
            </div>
          </div>

          <!-- Empty state -->
          <div v-else class="h-full flex flex-col items-center justify-center text-zinc-500 text-xs py-16">
            <Bot class="w-8 h-8 text-zinc-700 mb-2" />
            <span>请在左侧选择或添加供应商</span>
          </div>
        </div>
      </div>

      <!-- Footer Actions -->
      <div class="px-6 py-3.5 border-t border-zinc-800 bg-zinc-950 flex items-center justify-between">
        <!-- Delete Button -->
        <div>
          <button
            v-if="currentProvider.providerId"
            @click="handleDelete(currentProvider.providerId)"
            class="text-xs text-rose-400 hover:text-rose-300 flex items-center gap-1.5 transition px-2 py-1 rounded hover:bg-rose-950/20"
          >
            <Trash2 class="w-3.5 h-3.5" />
            <span>删除此供应商</span>
          </button>
        </div>

        <!-- Action Buttons -->
        <div class="flex items-center gap-2.5">
          <button 
            @click="emit('close')"
            class="px-4 py-2 rounded-xl text-xs text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 transition"
          >
            关闭
          </button>

          <button 
            @click="handleSaveProvider"
            :disabled="isSaving || !currentProvider.providerId"
            class="px-4 py-2 rounded-xl bg-purple-600 hover:bg-purple-500 active:bg-purple-700 text-white font-medium text-xs flex items-center gap-1.5 transition shadow-md shadow-purple-950/40 disabled:opacity-50"
          >
            <Loader2 v-if="isSaving" class="w-3.5 h-3.5 animate-spin" />
            <Save v-else class="w-3.5 h-3.5" />
            <span>保存并生效</span>
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
  animation: fadeIn 0.15s ease-out forwards;
}
</style>
