<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useAgentStore } from '@/stores/agentStore';
import { useUiStore } from '@/stores/uiStore';
import { useProviders } from '@/composables/useProviders';
import { useGeneralConfig } from '@/composables/useGeneralConfig';
import { readSelectedModel, useSession, writeSelectedModel } from '@/composables/useSession';
import type { ModelRef } from '@/types/message.types';
import { IconCheck, IconChevronDown, IconWarning, ICON_STROKE } from '@/components/primitives/icons';

/**
 * dsh ModelSelect: 28px chip in the composer's trailing group opening an
 * upward menu grouped by provider (sticky group titles, check on the selection).
 * When no model is configured, surfaces a clear warning tone and label.
 */
const props = withDefaults(defineProps<{ locked?: boolean }>(), { locked: false });

const emit = defineEmits<{ change: [model: ModelRef | null] }>();

const store = useAgentStore();
const ui = useUiStore();
const { providers, loaded, loading, error, ensureLoaded, refresh } = useProviders();
const { settings: generalSettings, ensureLoaded: ensureGeneralLoaded } = useGeneralConfig();
const { updateSessionModel } = useSession();

const open = ref(false);
const rootRef = ref<HTMLElement | null>(null);

interface Group {
  providerId: string;
  name: string;
  models: string[];
}

const groups = computed<Group[]>(() =>
  providers.value
    .filter((p) => p.models.length > 0)
    .map((p) => ({ providerId: p.providerId, name: p.name || p.providerId, models: p.models })),
);

const available = computed(() => groups.value.flatMap((g) => g.models.map((id) => ({ provider: g.providerId, id }))));
const hasModels = computed(() => available.value.length > 0);

const selected = ref<ModelRef | null>(null);

function sameModel(a: ModelRef | null | undefined, b: ModelRef | null | undefined) {
  return !!a && !!b && a.provider === b.provider && a.id === b.id;
}

function resolveSelection() {
  if (available.value.length === 0) {
    selected.value = null;
    writeSelectedModel(null);
    emit('change', null);
    return;
  }
  const sessionModel = store.currentSession?.model;
  if (sessionModel?.id && available.value.some((m) => sameModel(m, sessionModel))) {
    selected.value = sessionModel;
    emit('change', selected.value);
    return;
  }
  const saved = readSelectedModel();
  if (saved && available.value.some((m) => sameModel(m, saved))) {
    selected.value = saved;
    emit('change', selected.value);
    return;
  }
  if (generalSettings.value.defaultModel && generalSettings.value.defaultProvider) {
    const match = available.value.find(
      (m) => m.provider === generalSettings.value.defaultProvider && m.id === generalSettings.value.defaultModel,
    );
    if (match) {
      selected.value = match;
      emit('change', selected.value);
      return;
    }
  }
  selected.value = available.value[0] ?? null;
  writeSelectedModel(selected.value);
  emit('change', selected.value);
}

watch([available, () => store.currentSession?.model, generalSettings], resolveSelection, { immediate: true });

onMounted(() => {
  void ensureLoaded();
  void ensureGeneralLoaded();
});

const label = computed(() => {
  if (loading.value && !loaded.value) return '正在加载模型…';
  if (!hasModels.value) return '请先配置模型';
  return selected.value?.id ?? '选择模型';
});

const providerName = computed(() => {
  if (!selected.value) return '';
  return groups.value.find((g) => g.providerId === selected.value?.provider)?.name ?? selected.value.provider;
});

async function choose(model: ModelRef) {
  selected.value = model;
  writeSelectedModel(model);
  emit('change', model);
  open.value = false;
  if (store.currentSessionId) await updateSessionModel(store.currentSessionId, model);
}

function onPointerDown(event: PointerEvent) {
  if (!rootRef.value?.contains(event.target as Node)) open.value = false;
}
function onKeyDown(event: KeyboardEvent) {
  if (event.key === 'Escape') open.value = false;
}

watch(open, (isOpen) => {
  if (isOpen) {
    document.addEventListener('pointerdown', onPointerDown, true);
    document.addEventListener('keydown', onKeyDown, true);
  } else {
    document.removeEventListener('pointerdown', onPointerDown, true);
    document.removeEventListener('keydown', onKeyDown, true);
  }
});

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onPointerDown, true);
  document.removeEventListener('keydown', onKeyDown, true);
});

function toggle() {
  if (props.locked) return;
  open.value = !open.value;
  if (open.value) void refresh();
}

function goSettings() {
  open.value = false;
  ui.openSettings('models');
}

defineExpose({ selected, hasModels });
</script>

<template>
  <div ref="rootRef" class="modelSelect">
    <button
      type="button"
      :class="['trigger', { triggerWarning: loaded && !hasModels }]"
      aria-haspopup="menu"
      :aria-expanded="open"
      :aria-label="`选择模型，当前 ${label}`"
      :disabled="locked"
      :title="loaded && !hasModels ? '未检测到可用模型，请点击前往设置配置' : providerName ? `${providerName} / ${label}` : undefined"
      @click="toggle"
    >
      <IconWarning v-if="loaded && !hasModels" class="warningIcon" :size="14" :stroke-width="ICON_STROKE" />
      <span class="triggerLabel">{{ label }}</span>
      <IconChevronDown :class="['chevron', { chevronOpen: open }]" :size="14" :stroke-width="ICON_STROKE" />
    </button>

    <div v-if="open" class="menu" role="menu" aria-label="模型">
      <div v-if="error" class="error">
        <span>目录加载失败：{{ error }}</span>
        <button type="button" class="retry" @click="refresh()">重新加载</button>
      </div>
      <div v-if="loading && !loaded" class="status">正在刷新模型列表…</div>
      <div v-else-if="groups.length === 0" class="empty">
        <IconWarning class="emptyWarning" :size="16" :stroke-width="ICON_STROKE" />
        <span>尚未配置任何模型。</span>
        <button type="button" class="link" @click="goSettings">前往设置 →</button>
      </div>
      <div v-else class="groups">
        <section v-for="group in groups" :key="group.providerId" class="group" role="group" :aria-label="group.name">
          <div class="groupTitle">{{ group.name }}</div>
          <button
            v-for="id in group.models"
            :key="`${group.providerId}:${id}`"
            type="button"
            role="menuitemradio"
            :class="['option', { selected: sameModel(selected, { provider: group.providerId, id }) }]"
            :aria-checked="sameModel(selected, { provider: group.providerId, id })"
            @click="choose({ provider: group.providerId, id })"
          >
            <span class="optionCopy"><span class="modelName">{{ id }}</span></span>
            <span class="check">
              <IconCheck v-if="sameModel(selected, { provider: group.providerId, id })" :size="16" :stroke-width="ICON_STROKE" />
            </span>
          </button>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modelSelect {
  position: relative;
  display: inline-flex;
  min-width: 0;
}

.trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: min(360px, 45cqw);
  height: 28px;
  padding: 0 4px 0 8px;
  border: none;
  border-radius: 24px;
  background: transparent;
  color: var(--dsw-alias-label-secondary);
  font-size: 13px;
  line-height: 20px;
  font-weight: 500;
  cursor: pointer;
  min-width: 0;
}

.trigger:hover:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-hover);
}

.trigger:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--dsw-alias-border-l3);
}

.trigger:disabled {
  color: var(--dsw-alias-label-dimmed);
  cursor: default;
}

.triggerWarning {
  color: var(--dsw-alias-state-warn-label);
  background: color-mix(in srgb, var(--dsw-alias-state-warn-label) 10%, transparent);
}

.triggerWarning:hover:not(:disabled) {
  background: color-mix(in srgb, var(--dsw-alias-state-warn-label) 18%, transparent);
}

.warningIcon {
  flex: none;
  color: var(--dsw-alias-state-warn-label);
}

.triggerLabel {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chevron {
  flex: none;
  color: var(--dsw-alias-label-caption);
  transition: transform 120ms ease;
}

.chevronOpen {
  transform: rotate(180deg);
}

.menu {
  position: absolute;
  right: 0;
  bottom: calc(100% + 8px);
  z-index: 20;
  display: flex;
  flex-direction: column;
  width: max-content;
  min-width: min(240px, calc(100vw - 32px));
  max-width: min(420px, calc(100vw - 32px));
  max-height: min(360px, calc(100vh - 96px));
  box-sizing: border-box;
  padding: 4px;
  border-radius: 20px;
  background: var(--dsw-specific-menu);
  --dsw-elevation-stroke-color: var(--dsw-alias-border-l1);
  box-shadow: var(--dsw-elevation-prominent);
  color: var(--dsw-alias-label-primary);
  --dsh-scrollbar-thumb: var(--dsw-alias-scrollbar-bg-l2);
  --dsh-scrollbar-thumb-hover: var(--dsw-alias-scrollbar-hover-l2);
}

.status,
.empty {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px;
  color: var(--dsw-alias-label-tertiary);
  font-size: 13px;
  line-height: 20px;
  white-space: nowrap;
}

.emptyWarning {
  color: var(--dsw-alias-state-warn-label);
}

.link {
  border: none;
  background: transparent;
  padding: 0;
  color: var(--dsw-alias-state-business-primary);
  font-size: 13px;
  line-height: 20px;
  font-weight: 500;
  cursor: pointer;
}

.link:hover {
  text-decoration: underline;
}

.error {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
  padding: 7px 8px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 18px;
  background: var(--dsw-alias-interactive-bg-hover-danger);
  color: var(--dsw-alias-state-error-primary);
}

.retry {
  border: none;
  background: transparent;
  padding: 0;
  color: inherit;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}

.groups {
  overflow-y: auto;
  min-height: 0;
}

.group + .group {
  margin-top: 4px;
}

.groupTitle {
  position: sticky;
  top: 0;
  padding: 5px 8px 3px;
  background: var(--dsw-specific-menu);
  color: var(--dsw-alias-label-tertiary);
  font-size: 12px;
  line-height: 18px;
  font-weight: 500;
}

.option {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 100%;
  min-height: 38px;
  padding: 6px 8px;
  box-sizing: border-box;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--dsw-alias-label-primary);
  text-align: left;
  cursor: pointer;
}

.option:hover,
.option:focus-visible {
  background: var(--dsw-alias-interactive-bg-hover);
  outline: none;
}

.optionCopy {
  flex: 1;
  min-width: 0;
}

.modelName {
  display: block;
  font-size: 14px;
  line-height: 20px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.check {
  display: grid;
  place-items: center;
  flex: 0 0 18px;
  color: var(--dsw-alias-label-primary);
}
</style>
