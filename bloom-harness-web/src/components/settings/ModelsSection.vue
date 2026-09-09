<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import type { ProviderConfig } from '@/types/session.types';
import { useProviders } from '@/composables/useProviders';
import DsModal from '@/components/primitives/DsModal.vue';
import DsButton from '@/components/primitives/DsButton.vue';
import { IconPlus, ICON_STROKE } from '@/components/primitives/icons';
import ProviderEditor from './ProviderEditor.vue';

/**
 * dsh ModelsSection: providers as outlined cards (name, protocol tag, credential
 * dot, Edit / Delete), an inline editor inside the open card, a dashed "add"
 * button, and a delete confirmation modal.
 */
const { providers, loaded, loading, error, ensureLoaded, refresh, notifyUpdated } = useProviders();

onMounted(() => void ensureLoaded());

const openId = ref<string | null>(null);
const creating = ref(false);
const busy = ref(false);
const failure = ref<string | null>(null);
const saved = ref<string | null>(null);
let savedTimer: ReturnType<typeof setTimeout> | null = null;

const deleteTarget = ref<ProviderConfig | null>(null);
const deleting = ref(false);
const deleteFailure = ref<string | null>(null);

const existingIds = computed(() => providers.value.map((p) => p.providerId.toLowerCase()));

function protocolLabel(p: ProviderConfig) {
  return p.protocol === 'anthropic' ? 'Anthropic' : 'OpenAI 兼容';
}

function openEditor(id: string) {
  creating.value = false;
  failure.value = null;
  openId.value = openId.value === id ? null : id;
}

function startCreate() {
  openId.value = null;
  failure.value = null;
  creating.value = true;
}

function cancelEditor() {
  openId.value = null;
  creating.value = false;
  failure.value = null;
}

function flashSaved(name: string) {
  saved.value = name;
  if (savedTimer) clearTimeout(savedTimer);
  savedTimer = setTimeout(() => (saved.value = null), 4000);
}

async function save(draft: ProviderConfig) {
  busy.value = true;
  failure.value = null;
  try {
    const res = await fetch('/api/config/providers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        providerId: draft.providerId,
        name: draft.name,
        baseUrl: draft.baseUrl,
        apiKey: draft.apiKey,
        protocol: draft.protocol,
        models: draft.models,
      }),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok || data.error) throw new Error(data.error || `HTTP ${res.status}`);
    await refresh();
    notifyUpdated();
    flashSaved(draft.name || draft.providerId);
    cancelEditor();
  } catch (e: any) {
    failure.value = `保存失败：${e?.message || e}`;
  } finally {
    busy.value = false;
  }
}

async function confirmDelete() {
  const target = deleteTarget.value;
  if (!target) return;
  deleting.value = true;
  deleteFailure.value = null;
  try {
    const res = await fetch(`/api/config/providers/${encodeURIComponent(target.providerId)}`, { method: 'DELETE' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    await refresh();
    notifyUpdated();
    if (openId.value === target.providerId) openId.value = null;
    deleteTarget.value = null;
  } catch (e: any) {
    deleteFailure.value = `删除失败：${e?.message || e}`;
  } finally {
    deleting.value = false;
  }
}
</script>

<template>
  <div class="section">
    <h2 class="title">模型</h2>
    <p class="intro">填入各服务商的 API 地址与密钥即可使用其模型；模型目录决定输入框中可选的模型。</p>
    <p v-if="saved" class="savedNotice" role="status" aria-live="polite">已保存 {{ saved }}。</p>

    <template v-if="error && !loaded">
      <p class="error">加载服务商目录失败：{{ error }}</p>
      <button type="button" class="secondaryButton" @click="refresh()">重试</button>
    </template>

    <ul class="rows">
      <li v-if="loaded && providers.length === 0 && !creating" class="emptyCard">
        尚未配置任何服务商。添加一个服务商并填入 API 密钥后即可开始对话。
      </li>
      <li v-for="p in providers" :key="p.providerId" class="rowCard">
        <div class="rowHead">
          <span class="rowIdentity">
            <span class="rowName">{{ p.name || p.providerId }}</span>
            <span class="rowTag">{{ protocolLabel(p) }}</span>
            <span
              :class="['credentialDot', p.isConfigured ? 'credentialDotConfigured' : 'credentialDotMissing']"
              role="img"
              :aria-label="p.isConfigured ? 'API 密钥已配置' : 'API 密钥缺失'"
              :title="p.isConfigured ? 'API 密钥已配置' : 'API 密钥缺失'"
            />
            <span class="rowMeta">{{ p.models.length }} 个模型</span>
          </span>
          <span class="rowActions">
            <button type="button" class="secondaryButton small" :aria-label="`编辑 ${p.name}`" @click="openEditor(p.providerId)">
              {{ openId === p.providerId ? '收起' : '编辑' }}
            </button>
            <button type="button" class="dangerButton small" :aria-label="`删除 ${p.name}`" @click="deleteTarget = p">删除</button>
          </span>
        </div>
        <ProviderEditor
          v-if="openId === p.providerId"
          :provider="p"
          mode="edit"
          :existing-ids="existingIds"
          :busy="busy"
          :failure="failure"
          @save="save"
          @cancel="cancelEditor"
        />
      </li>
    </ul>

    <div class="addBlock">
      <div v-if="creating" class="addCard">
        <ProviderEditor
          :provider="null"
          mode="create"
          :existing-ids="existingIds"
          :busy="busy"
          :failure="failure"
          @save="save"
          @cancel="cancelEditor"
        />
      </div>
      <div v-else class="addActions">
        <button type="button" class="addButton" :disabled="loading && !loaded" @click="startCreate">
          <IconPlus :size="14" :stroke-width="ICON_STROKE" />
          添加服务商
        </button>
      </div>
    </div>

    <DsModal
      :open="deleteTarget !== null"
      :title="`删除 ${deleteTarget?.name || deleteTarget?.providerId || ''}？`"
      :description="`删除 ${deleteTarget?.name || ''} 会移除其配置和存储的 API 密钥。`"
      width="min(480px, 100%)"
      @close="deleteTarget = null"
    >
      <p v-if="deleteFailure" class="error">{{ deleteFailure }}</p>
      <template #footer>
        <DsButton variant="outline" @click="deleteTarget = null">取消</DsButton>
        <DsButton variant="danger" :disabled="deleting" @click="confirmDelete">
          {{ deleting ? '正在删除…' : `删除 ${deleteTarget?.name || ''}` }}
        </DsButton>
      </template>
    </DsModal>
  </div>
</template>

<style scoped>
.section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 720px;
  padding-top: 8px;
  color: var(--dsw-alias-label-primary);
}

.title {
  margin: 0;
  font-size: 16px;
  line-height: 24px;
  font-weight: 500;
  color: var(--dsw-alias-label-primary);
}

.intro {
  margin: 0;
  font-size: 14px;
  line-height: 22px;
  color: var(--dsw-alias-label-tertiary);
}

.savedNotice {
  margin: 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--dsw-alias-state-success-primary);
}

.error {
  margin: 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--dsw-alias-state-error-primary);
}

.rows {
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.emptyCard {
  padding: 16px;
  border: 1px dashed var(--dsw-alias-border-l3);
  border-radius: 16px;
  font-size: 13px;
  line-height: 20px;
  color: var(--dsw-alias-label-tertiary);
}

.rowCard {
  border: 0.5px solid var(--dsw-alias-border-l4);
  border-radius: 16px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rowHead {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rowIdentity {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.rowName {
  font-size: 14px;
  line-height: 22px;
  font-weight: 500;
  color: var(--dsw-alias-label-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rowTag {
  flex: none;
  padding: 1px 6px;
  border: 0.5px solid var(--dsw-alias-border-l3);
  border-radius: 4px;
  font-size: 11px;
  line-height: 16px;
  color: var(--dsw-alias-label-secondary);
}

.rowMeta {
  flex: none;
  font-size: 12px;
  line-height: 18px;
  color: var(--dsw-alias-label-tertiary);
}

.credentialDot {
  box-sizing: border-box;
  display: inline-block;
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  corner-shape: round;
}

.credentialDotConfigured {
  background: var(--dsw-alias-state-success-primary);
}

.credentialDotMissing {
  background: var(--dsw-alias-state-error-primary);
}

.rowActions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

.secondaryButton,
.dangerButton,
.addButton {
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
  white-space: nowrap;
}

.secondaryButton,
.addButton {
  border: 0.5px solid var(--dsw-alias-border-l3);
  background: transparent;
  color: var(--dsw-alias-label-primary);
}

.secondaryButton:hover:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-hover-solid);
}

.addButton:hover:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-hover);
}

.dangerButton {
  background: transparent;
  color: var(--dsw-alias-state-error-primary);
}

.dangerButton:hover:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-hover-danger);
}

.small {
  height: 28px;
  padding: 0 10px;
  border-radius: 14px;
  font-size: 12px;
  line-height: 18px;
}

.secondaryButton:disabled,
.dangerButton:disabled,
.addButton:disabled {
  opacity: 0.4;
  cursor: default;
}

.addBlock {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.addActions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.addButton {
  flex: 1 1 0;
  min-width: 180px;
  gap: 6px;
  height: 44px;
  border: 1px dashed var(--dsw-alias-border-l3);
  border-radius: 16px;
}

.addCard {
  border-radius: 12px;
  background: var(--dsw-alias-bg-module-platform);
  display: flex;
  flex-direction: column;
}
</style>
