<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import type { ProviderConfig } from '@/types/session.types';
import DsModal from '@/components/primitives/DsModal.vue';
import DsButton from '@/components/primitives/DsButton.vue';
import { IconEye, IconEyeOff, IconPlus, IconTrash, IconZap, ICON_STROKE } from '@/components/primitives/icons';

/**
 * dsh ProviderEditor / CustomProviderCard: the in-card provider form —
 * identity, protocol, base URL, API key (with connectivity test), and the
 * model catalog (manual entries + "fetch available models" picker).
 */
const props = defineProps<{
  provider: ProviderConfig | null;
  mode: 'edit' | 'create';
  existingIds: string[];
  busy: boolean;
  failure: string | null;
}>();

const emit = defineEmits<{
  save: [draft: ProviderConfig];
  cancel: [];
}>();

const draft = reactive<ProviderConfig>({
  providerId: props.provider?.providerId ?? '',
  name: props.provider?.name ?? '',
  baseUrl: props.provider?.baseUrl ?? '',
  apiKey: props.provider?.apiKey ?? '',
  protocol: props.provider?.protocol ?? 'openai',
  models: [...(props.provider?.models ?? [])],
  isConfigured: props.provider?.isConfigured ?? false,
});

watch(
  () => props.provider,
  (p) => {
    draft.providerId = p?.providerId ?? '';
    draft.name = p?.name ?? '';
    draft.baseUrl = p?.baseUrl ?? '';
    draft.apiKey = p?.apiKey ?? '';
    draft.protocol = p?.protocol ?? 'openai';
    draft.models = [...(p?.models ?? [])];
    draft.isConfigured = p?.isConfigured ?? false;
  },
);

const showKey = ref(false);
const newModel = ref('');

const ROUTE_RE = /^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/;
const routeInvalid = computed(() => props.mode === 'create' && draft.providerId.length > 0 && !ROUTE_RE.test(draft.providerId));
const routeTaken = computed(() => props.mode === 'create' && props.existingIds.includes(draft.providerId.toLowerCase()));
const keyMasked = computed(() => draft.apiKey.includes('••••'));
const keyHint = computed(() => (props.mode === 'edit' && props.provider?.isConfigured ? '已配置——输入新值可替换' : '输入 API 密钥'));

const ready = computed(() => {
  if (props.mode === 'create' && (!draft.providerId || routeInvalid.value || routeTaken.value)) return false;
  if (!draft.baseUrl.trim()) return false;
  return true;
});

function addModel() {
  const id = newModel.value.trim();
  if (!id) return;
  if (!draft.models.includes(id)) draft.models.push(id);
  newModel.value = '';
}

function removeModel(index: number) {
  draft.models.splice(index, 1);
}

// ---- connectivity test ----
const testing = ref(false);
const testResult = ref<{ ok: boolean; message: string; latencyMs?: number } | null>(null);

async function testConnection() {
  testing.value = true;
  testResult.value = null;
  try {
    const res = await fetch('/api/config/providers/test', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ providerId: draft.providerId, baseUrl: draft.baseUrl, apiKey: draft.apiKey }),
    });
    const data = await res.json();
    testResult.value = { ok: !!data.ok, message: data.message ?? (data.ok ? '连接成功' : '连接失败'), latencyMs: data.latencyMs };
  } catch (e: any) {
    testResult.value = { ok: false, message: e?.message || String(e) };
  } finally {
    testing.value = false;
  }
}

// ---- fetch models picker ----
const fetching = ref(false);
const fetchError = ref<string | null>(null);
const pickerOpen = ref(false);
const candidates = ref<string[]>([]);
const picked = ref<Set<string>>(new Set());
const candidateQuery = ref('');

const filteredCandidates = computed(() => {
  const q = candidateQuery.value.trim().toLowerCase();
  return q ? candidates.value.filter((c) => c.toLowerCase().includes(q)) : candidates.value;
});

async function fetchModels() {
  if (!draft.baseUrl.trim()) {
    fetchError.value = '请先填写 API 地址，再获取。';
    return;
  }
  fetching.value = true;
  fetchError.value = null;
  try {
    const res = await fetch('/api/config/providers/fetch-models', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ providerId: draft.providerId, baseUrl: draft.baseUrl, apiKey: draft.apiKey }),
    });
    const data = await res.json();
    if (!data.ok) throw new Error(data.message || '获取失败');
    const list: string[] = Array.isArray(data.models) ? data.models : [];
    if (list.length === 0) {
      fetchError.value = '该提供方没有列出任何模型，请手动添加。';
      return;
    }
    candidates.value = list;
    picked.value = new Set(list.filter((m) => draft.models.includes(m)));
    candidateQuery.value = '';
    pickerOpen.value = true;
  } catch (e: any) {
    fetchError.value = e?.message || String(e);
  } finally {
    fetching.value = false;
  }
}

function togglePick(id: string) {
  const next = new Set(picked.value);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  picked.value = next;
}

function pickAll() {
  const all = filteredCandidates.value.every((c) => picked.value.has(c));
  const next = new Set(picked.value);
  filteredCandidates.value.forEach((c) => (all ? next.delete(c) : next.add(c)));
  picked.value = next;
}

function adoptPicked() {
  for (const id of picked.value) if (!draft.models.includes(id)) draft.models.push(id);
  pickerOpen.value = false;
}

function submit() {
  if (!ready.value || props.busy) return;
  emit('save', {
    providerId: draft.providerId.trim().toLowerCase(),
    name: draft.name.trim() || draft.providerId.trim(),
    baseUrl: draft.baseUrl.trim(),
    apiKey: draft.apiKey,
    protocol: draft.protocol,
    models: [...draft.models],
    isConfigured: draft.isConfigured,
  });
}
</script>

<template>
  <div class="editor">
    <div class="editorHeader">
      <span class="editorTitle">{{ mode === 'create' ? '添加服务商' : provider?.name || provider?.providerId }}</span>
      <span v-if="mode === 'edit' && provider && provider.name !== provider.providerId" class="editorRoute">{{ provider.providerId }}</span>
    </div>

    <div v-if="mode === 'create'" class="field">
      <span class="fieldLabel">Provider ID</span>
      <input v-model="draft.providerId" class="input" type="text" placeholder="deepseek" autocomplete="off" spellcheck="false" />
      <p v-if="routeInvalid" class="error">需以小写字母开头，之后可用小写字母、数字和短横线。</p>
      <p v-else-if="routeTaken" class="error">已有服务商使用了这个 ID。</p>
      <p v-else class="advancedHint">以小写字母开头的标识，用于在请求中唯一标识该服务商。</p>
    </div>

    <div class="fieldGrid">
      <div class="field">
        <span class="fieldLabel">显示名称</span>
        <input v-model="draft.name" class="input" type="text" :placeholder="draft.providerId || '显示名称'" />
      </div>
      <div class="field">
        <span class="fieldLabel">API 协议</span>
        <select v-model="draft.protocol" class="input selectInput">
          <option value="openai">OpenAI 兼容</option>
          <option value="anthropic">Anthropic Messages</option>
        </select>
      </div>
    </div>

    <div class="field">
      <span class="fieldLabel">API 地址</span>
      <input v-model="draft.baseUrl" class="input mono" type="text" placeholder="https://api.deepseek.com/v1" autocomplete="off" spellcheck="false" />
    </div>

    <div class="field">
      <span class="fieldLabel">
        API 密钥
        <button type="button" class="linkButton" :disabled="testing || !draft.baseUrl.trim()" @click="testConnection">
          <IconZap :size="12" :stroke-width="ICON_STROKE" />
          {{ testing ? '正在探测…' : '测试连通性' }}
        </button>
      </span>
      <span class="keyWrap">
        <input
          v-model="draft.apiKey"
          class="input mono"
          :type="showKey && !keyMasked ? 'text' : 'password'"
          :placeholder="keyHint"
          autocomplete="off"
          spellcheck="false"
        />
        <button
          type="button"
          class="iconButton keyToggle"
          :aria-label="showKey ? '隐藏密钥' : '显示密钥'"
          :disabled="keyMasked"
          @click="showKey = !showKey"
        >
          <IconEyeOff v-if="showKey" :size="14" :stroke-width="ICON_STROKE" />
          <IconEye v-else :size="14" :stroke-width="ICON_STROKE" />
        </button>
      </span>
      <p v-if="testResult" :class="testResult.ok ? 'savedNotice' : 'error'">
        {{ testResult.message }}<span v-if="testResult.latencyMs !== undefined"> · {{ testResult.latencyMs }} ms</span>
      </p>
    </div>

    <section class="modelCatalog" aria-label="模型目录">
      <div class="modelListHead">
        <div class="modelCatalogHeading">
          <span class="modelCatalogTitle">模型目录</span>
          <span class="modelCatalogMeta">{{ draft.models.length ? `已登记 ${draft.models.length} 个模型` : '尚未添加模型' }}</span>
        </div>
        <button type="button" class="linkButton" :disabled="fetching" @click="fetchModels">
          {{ fetching ? '正在询问提供方…' : '获取可用模型' }}
        </button>
      </div>
      <p v-if="fetchError" class="error">{{ fetchError }}</p>
      <div v-if="draft.models.length === 0" class="modelEmptyCallout">
        <span class="calloutIcon">💡</span>
        <span class="calloutText">Spring AI 路由引擎要求：服务商至少需包含一个模型 ID，以便建立路由映射。请点击上方「获取可用模型」或在下方手动添加。</span>
      </div>
      <div v-else class="modelList">
        <div v-for="(model, index) in draft.models" :key="model" class="modelEntry">
          <div class="modelRow">
            <span class="modelId">{{ model }}</span>
            <button type="button" class="iconButton iconButtonDanger" :aria-label="`删除模型 ${model}`" @click="removeModel(index)">
              <IconTrash :size="14" :stroke-width="ICON_STROKE" />
            </button>
          </div>
        </div>
      </div>
      <div class="addModelRow">
        <input
          v-model="newModel"
          class="input mono"
          type="text"
          placeholder="输入模型 ID 并回车（如 deepseek-chat）"
          autocomplete="off"
          spellcheck="false"
          @keydown.enter.prevent="addModel"
        />
        <button type="button" class="addModelButton" :disabled="!newModel.trim()" @click="addModel">
          <IconPlus :size="14" :stroke-width="ICON_STROKE" />
          添加模型
        </button>
      </div>
    </section>

    <p v-if="failure" class="error">{{ failure }}</p>

    <div class="editorActions">
      <button type="button" class="secondaryButton" :disabled="busy" @click="emit('cancel')">取消</button>
      <button type="button" class="primaryButton" :disabled="!ready || busy" @click="submit">
        {{ busy ? (mode === 'create' ? '创建中…' : '保存中…') : mode === 'create' ? '创建服务商' : '保存' }}
      </button>
    </div>

    <DsModal :open="pickerOpen" title="选择要添加的模型" description="以下是提供方的可用模型，勾选要添加的模型。" width="min(520px, 100%)" @close="pickerOpen = false">
      <div class="candidateToolbar">
        <input v-model="candidateQuery" class="input candidateSearch" type="search" placeholder="搜索模型" />
        <DsButton variant="ghost" size="sm" @click="pickAll">
          {{ filteredCandidates.length && filteredCandidates.every((c) => picked.has(c)) ? '取消全选' : '全选' }}
        </DsButton>
      </div>
      <ul v-if="filteredCandidates.length" class="candidateList">
        <li v-for="c in filteredCandidates" :key="c" class="candidate">
          <label class="candidateLabel">
            <input type="checkbox" :checked="picked.has(c)" @change="togglePick(c)" />
            <span class="candidateId">{{ c }}</span>
          </label>
        </li>
      </ul>
      <p v-else class="candidateEmpty" role="status">没有匹配的模型。</p>
      <template #footer>
        <DsButton variant="outline" @click="pickerOpen = false">取消</DsButton>
        <DsButton variant="outline" :disabled="picked.size === 0" @click="adoptPicked">添加所选（{{ picked.size }}）</DsButton>
      </template>
    </DsModal>
  </div>
</template>

<style scoped>
.editor {
  border-radius: 12px;
  background: var(--dsw-alias-bg-module-platform);
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.editorHeader {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.editorTitle {
  font-size: 14px;
  line-height: 22px;
  font-weight: 500;
  color: var(--dsw-alias-label-primary);
}

.editorRoute {
  font-size: 12px;
  line-height: 18px;
  color: var(--dsw-alias-label-tertiary);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.fieldGrid {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  gap: 12px;
}

.fieldLabel {
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
  line-height: 18px;
  font-weight: 500;
  color: var(--dsw-alias-label-secondary);
}

.input {
  box-sizing: border-box;
  width: 100%;
  height: 32px;
  padding: 0 10px;
  border: 0.5px solid var(--dsw-alias-border-l4);
  border-radius: 8px;
  font: inherit;
  font-size: 14px;
  line-height: 22px;
  background: var(--dsw-alias-bg-layer-1);
  color: var(--dsw-alias-label-primary);
}

.input:focus {
  outline: none;
  border-color: var(--dsw-alias-brand-primary);
}

.input::placeholder {
  color: var(--dsw-alias-label-dimmed);
}

.input:disabled {
  opacity: 0.6;
  cursor: default;
}

.mono {
  font-family: var(--ds-font-family-code);
  font-size: 13px;
}

.selectInput {
  appearance: none;
  padding-right: 32px;
  cursor: pointer;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12' fill='none'%3E%3Cpath d='M3 4.5L6 7.5L9 4.5' stroke='%2381858C' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  background-size: 12px 12px;
}

.keyWrap {
  position: relative;
  display: flex;
  align-items: center;
}

.keyWrap .input {
  padding-right: 36px;
}

.keyToggle {
  position: absolute;
  right: 4px;
}

.linkButton {
  box-sizing: border-box;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  border: none;
  border-radius: 14px;
  background: transparent;
  color: var(--dsw-alias-label-tertiary);
  font: inherit;
  font-size: 12px;
  line-height: 18px;
  cursor: pointer;
}

.linkButton:hover:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-hover);
  color: var(--dsw-alias-label-secondary);
}

.linkButton:disabled {
  opacity: 0.4;
  cursor: default;
}

.advancedHint {
  margin: 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--dsw-alias-label-tertiary);
}

.error {
  margin: 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--dsw-alias-state-error-primary);
}

.savedNotice {
  margin: 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--dsw-alias-state-success-primary);
}

.modelCatalog {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 12px;
  border-top: 0.5px solid var(--dsw-alias-border-l2);
}

.modelListHead {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.modelCatalogHeading {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.modelCatalogTitle {
  font-size: 12px;
  line-height: 18px;
  font-weight: 500;
  color: var(--dsw-alias-label-secondary);
}

.modelCatalogMeta {
  margin: 0;
  color: var(--dsw-alias-label-tertiary);
  font-size: 12px;
  line-height: 18px;
}

.modelEmpty {
  margin: 0;
  padding: 12px;
  border: 1px dashed var(--dsw-alias-border-l3);
  border-radius: 8px;
  text-align: center;
  color: var(--dsw-alias-label-tertiary);
  font-size: 12px;
  line-height: 18px;
}

.modelEmptyCallout {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--dsw-static-amber-500) 10%, transparent);
  border: 0.5px solid color-mix(in srgb, var(--dsw-static-amber-500) 30%, transparent);
  font-size: 12px;
  line-height: 18px;
  color: var(--dsw-alias-label-primary);
}

.calloutIcon {
  flex: none;
  font-size: 14px;
  line-height: 18px;
}

.calloutText {
  flex: 1;
}

.modelList {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.modelEntry {
  border: 0.5px solid var(--dsw-alias-border-l4);
  border-radius: 10px;
  padding: 6px;
}

.modelRow {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 6px;
}

.modelId {
  min-width: 0;
  padding: 0 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--ds-font-family-code);
  font-size: 13px;
  line-height: 22px;
  color: var(--dsw-alias-label-primary);
}

.iconButton {
  box-sizing: border-box;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--dsw-alias-label-tertiary);
  cursor: pointer;
}

.iconButton:hover:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-hover);
  color: var(--dsw-alias-label-primary);
}

.iconButton:disabled {
  cursor: default;
  opacity: 0.4;
}

.iconButtonDanger:hover:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-hover-danger);
  color: var(--dsw-alias-state-error-primary);
}

.addModelRow {
  display: flex;
  align-items: center;
  gap: 8px;
}

.addModelButton {
  box-sizing: border-box;
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  border: 0.5px solid var(--dsw-alias-border-l3);
  border-radius: 14px;
  background: transparent;
  color: var(--dsw-alias-label-primary);
  font: inherit;
  font-size: 12px;
  line-height: 18px;
  cursor: pointer;
  white-space: nowrap;
}

.addModelButton:hover:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-hover);
}

.addModelButton:disabled {
  opacity: 0.4;
  cursor: default;
}

.editorActions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.primaryButton,
.secondaryButton {
  box-sizing: border-box;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 36px;
  padding: 0 14px;
  border: none;
  border-radius: 18px;
  font: inherit;
  font-size: 14px;
  line-height: 22px;
  cursor: pointer;
}

.primaryButton {
  background: var(--dsw-alias-button-primary-fill);
  color: var(--dsw-alias-label-primary-foreground);
}

.primaryButton:hover:not(:disabled) {
  background: var(--dsw-alias-button-primary-hover);
}

.secondaryButton {
  border: 0.5px solid var(--dsw-alias-border-l3);
  background: transparent;
  color: var(--dsw-alias-label-primary);
}

.secondaryButton:hover:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-hover-solid);
}

.primaryButton:disabled,
.secondaryButton:disabled {
  opacity: 0.4;
  cursor: default;
}

.candidateToolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.candidateSearch {
  min-width: 0;
  flex: 1 1 240px;
}

.candidateList {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 320px;
  margin: 0;
  overflow-y: auto;
  padding: 0;
  list-style: none;
}

.candidate {
  border-radius: 6px;
}

.candidate:hover {
  background: var(--dsw-alias-interactive-bg-hover);
}

.candidateLabel {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  cursor: pointer;
}

.candidateLabel input {
  accent-color: var(--dsw-alias-button-primary-fill);
}

.candidateId {
  flex: 1 1 auto;
  font-family: var(--ds-font-family-code);
  font-size: 13px;
  overflow-wrap: anywhere;
}

.candidateEmpty {
  margin: 24px 0;
  color: var(--dsw-alias-label-secondary);
  font-size: 13px;
  line-height: 20px;
  text-align: center;
}
</style>
