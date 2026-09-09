<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useTheme, FONT_SIZE_MAX, FONT_SIZE_MIN, type ThemePreference } from '@/theme/useTheme';
import { useGeneralConfig, DEFAULT_SYSTEM_PROMPT } from '@/composables/useGeneralConfig';
import { useProviders } from '@/composables/useProviders';
import {
  IconChevronDown,
  IconChevronUp,
  IconDark,
  IconFollowSystem,
  IconLight,
  IconRefresh,
  ICON_STROKE,
} from '@/components/primitives/icons';

/**
 * dsh General section:
 * - AppearanceRow (three theme cubes)
 * - FontSizeRow (stepper pill)
 * - AI Runtime Preferences (Default Model, Temperature stepper, MaxTokens, SystemPrompt)
 * Rows follow the settings row pattern (16px padding, 0.5px hairline, title 14/22 + description 12/18).
 */
const { theme, setPreference, setFontSize } = useTheme();
const { settings, fetchGeneralSettings, saveGeneralSettings } = useGeneralConfig();
const { providers, ensureLoaded: ensureProvidersLoaded } = useProviders();

const cubes: Array<{ id: ThemePreference; label: string; icon: typeof IconLight }> = [
  { id: 'light', label: '浅色', icon: IconLight },
  { id: 'dark', label: '深色', icon: IconDark },
  { id: 'system', label: '跟随系统', icon: IconFollowSystem },
];

const canDecrease = computed(() => theme.fontSize > FONT_SIZE_MIN);
const canIncrease = computed(() => theme.fontSize < FONT_SIZE_MAX);

// ---- AI General Settings State ----
const savedNotice = ref(false);
let saveTimer: ReturnType<typeof setTimeout> | null = null;

function flashSaved() {
  savedNotice.value = true;
  if (saveTimer) clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    savedNotice.value = false;
  }, 2500);
}

onMounted(() => {
  void fetchGeneralSettings();
  void ensureProvidersLoaded();
});

const allModels = computed(() => {
  const result: Array<{ providerId: string; providerName: string; modelId: string; combined: string }> = [];
  for (const p of providers.value) {
    for (const m of p.models) {
      result.push({
        providerId: p.providerId,
        providerName: p.name || p.providerId,
        modelId: m,
        combined: `${p.providerId}:${m}`,
      });
    }
  }
  return result;
});

const currentCombinedModel = computed(() => {
  if (!settings.value.defaultProvider || !settings.value.defaultModel) return '';
  return `${settings.value.defaultProvider}:${settings.value.defaultModel}`;
});

function onDefaultModelChange(event: Event) {
  const target = event.target as HTMLSelectElement;
  const val = target.value;
  if (!val) {
    void saveGeneralSettings({ defaultProvider: '', defaultModel: '' }).then(flashSaved);
    return;
  }
  const [provider, ...rest] = val.split(':');
  const model = rest.join(':');
  void saveGeneralSettings({ defaultProvider: provider, defaultModel: model }).then(flashSaved);
}

const canDecreaseTemp = computed(() => settings.value.temperature > 0.05);
const canIncreaseTemp = computed(() => settings.value.temperature < 1.95);

function adjustTemperature(delta: number) {
  const next = Math.max(0, Math.min(2, Math.round((settings.value.temperature + delta) * 10) / 10));
  void saveGeneralSettings({ temperature: next }).then(flashSaved);
}

function onMaxTokensChange(event: Event) {
  const target = event.target as HTMLInputElement;
  const num = parseInt(target.value, 10);
  if (Number.isFinite(num) && num >= 128 && num <= 65536) {
    void saveGeneralSettings({ maxTokens: num }).then(flashSaved);
  }
}

let promptDebounceTimer: ReturnType<typeof setTimeout> | null = null;
function onSystemPromptInput(event: Event) {
  const target = event.target as HTMLTextAreaElement;
  const text = target.value;
  if (promptDebounceTimer) clearTimeout(promptDebounceTimer);
  promptDebounceTimer = setTimeout(() => {
    void saveGeneralSettings({ systemPrompt: text }).then(flashSaved);
  }, 600);
}

function resetSystemPrompt() {
  if (promptDebounceTimer) clearTimeout(promptDebounceTimer);
  settings.value.systemPrompt = DEFAULT_SYSTEM_PROMPT;
  void saveGeneralSettings({ systemPrompt: DEFAULT_SYSTEM_PROMPT }).then(flashSaved);
}
</script>

<template>
  <div class="section">
    <!-- 外观 -->
    <div class="group">
      <div class="title">外观</div>
      <div class="cubeRow">
        <button
          v-for="cube in cubes"
          :key="cube.id"
          type="button"
          :class="['themeCube', { selected: theme.preference === cube.id }]"
          :aria-pressed="theme.preference === cube.id"
          @click="setPreference(cube.id)"
        >
          <component :is="cube.icon" :size="16" :stroke-width="ICON_STROKE" />
          <span>{{ cube.label }}</span>
        </button>
      </div>
    </div>

    <!-- 字号大小 -->
    <div class="row">
      <div class="rowText">
        <div class="title">字号大小</div>
        <div class="desc">仅影响会话内容的字号</div>
      </div>
      <div class="stepper">
        <span class="value">{{ theme.fontSize }}</span>
        <span class="unit">px</span>
        <span class="arrows">
          <button type="button" class="arrow" aria-label="增大字号" :disabled="!canIncrease" @click="setFontSize(theme.fontSize + 1)">
            <IconChevronUp :size="9" :stroke-width="2" />
          </button>
          <button type="button" class="arrow" aria-label="减小字号" :disabled="!canDecrease" @click="setFontSize(theme.fontSize - 1)">
            <IconChevronDown :size="9" :stroke-width="2" />
          </button>
        </span>
      </div>
    </div>

    <!-- AI 运行时偏好头部 -->
    <div class="groupSubhead">
      <div class="titleWithBadge">
        <span class="title">AI 运行时偏好</span>
        <span v-if="savedNotice" class="savedBadge">已同步到后端</span>
      </div>
      <div class="desc">全局默认模型、采样温度与系统提示词（与后端 <code>/api/config/general</code> 保持同步）。</div>
    </div>

    <!-- 默认模型 -->
    <div class="row">
      <div class="rowText">
        <div class="title">默认模型</div>
        <div class="desc">新建会话与智能体循环未指派时的默认模型</div>
      </div>
      <div class="selectWrap">
        <select class="dshSelect" :value="currentCombinedModel" @change="onDefaultModelChange">
          <option value="">（不指定，自动兜底首个可用模型）</option>
          <option v-for="item in allModels" :key="item.combined" :value="item.combined">
            {{ item.modelId }} ({{ item.providerName }})
          </option>
        </select>
        <IconChevronDown class="selectChevron" :size="12" :stroke-width="ICON_STROKE" />
      </div>
    </div>

    <!-- 采样温度 -->
    <div class="row">
      <div class="rowText">
        <div class="title">采样温度 (Temperature)</div>
        <div class="desc">值越高越富有创意，值越低输出越稳定聚焦（默认 0.7）</div>
      </div>
      <div class="stepper">
        <span class="value">{{ Number(settings.temperature).toFixed(1) }}</span>
        <span class="arrows">
          <button type="button" class="arrow" aria-label="调高温度" :disabled="!canIncreaseTemp" @click="adjustTemperature(0.1)">
            <IconChevronUp :size="9" :stroke-width="2" />
          </button>
          <button type="button" class="arrow" aria-label="调低温度" :disabled="!canDecreaseTemp" @click="adjustTemperature(-0.1)">
            <IconChevronDown :size="9" :stroke-width="2" />
          </button>
        </span>
      </div>
    </div>

    <!-- 最大生成 Token -->
    <div class="row">
      <div class="rowText">
        <div class="title">最大输出限制 (Max Tokens)</div>
        <div class="desc">单次模型推理响应的最大 Token 生成预算（默认 4096）</div>
      </div>
      <div class="inputPill">
        <input
          class="numInput"
          type="number"
          step="256"
          min="128"
          max="65536"
          :value="settings.maxTokens"
          @change="onMaxTokensChange"
        />
        <span class="unit">tokens</span>
      </div>
    </div>

    <!-- 全局系统提示词 -->
    <div class="promptArea">
      <div class="promptHeader">
        <div class="rowText">
          <div class="title">系统提示词 (System Prompt)</div>
          <div class="desc">注入给智能体 ReAct 自主循环的全局顶层角色与行为指示</div>
        </div>
        <button type="button" class="resetButton" title="恢复默认系统提示词" @click="resetSystemPrompt">
          <IconRefresh :size="12" :stroke-width="ICON_STROKE" />
          恢复默认
        </button>
      </div>
      <textarea
        class="promptTextarea"
        rows="4"
        :value="settings.systemPrompt"
        placeholder="输入全局系统提示词…"
        @input="onSystemPromptInput"
      />
    </div>
  </div>
</template>

<style scoped>
.section {
  display: flex;
  flex-direction: column;
  width: 100%;
}

.section > :last-child {
  border-bottom: none;
}

.group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px 0;
  border-bottom: 0.5px solid var(--dsw-alias-border-l2);
}

.groupSubhead {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 24px 0 8px;
  border-bottom: 0.5px solid var(--dsw-alias-border-l2);
}

.titleWithBadge {
  display: flex;
  align-items: center;
  gap: 8px;
}

.savedBadge {
  font-size: 11px;
  line-height: 16px;
  padding: 1px 7px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--dsw-static-green-500) 15%, transparent);
  color: var(--dsw-static-green-500);
  font-weight: 500;
}

.title {
  font-size: 14px;
  font-weight: 400;
  line-height: 22px;
  color: var(--dsw-alias-label-primary);
}

.cubeRow {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: stretch;
}

.themeCube {
  flex: 1 1 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 20px 32px;
  border: 0.5px solid var(--dsw-alias-border-l4);
  border-radius: 20px;
  background: transparent;
  font-size: 14px;
  line-height: 22px;
  color: var(--dsw-alias-label-primary);
  cursor: pointer;
}

.themeCube:hover:not(.selected) {
  background: var(--dsw-alias-interactive-bg-hover);
}

.selected {
  background: var(--dsw-alias-bg-module-platform);
  border-color: var(--dsw-static-neutral-bluish-400);
}

.row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 0;
  border-bottom: 0.5px solid var(--dsw-alias-border-l2);
}

.rowText {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-right: 24px;
}

.desc {
  font-size: 12px;
  font-weight: 400;
  line-height: 18px;
  color: var(--dsw-alias-label-tertiary);
}

.desc code {
  font-family: inherit;
  font-size: 11px;
  padding: 1px 4px;
  border-radius: 4px;
  background: var(--dsw-alias-bg-module-platform);
  color: var(--dsw-alias-label-secondary);
}

.stepper {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-width: 72px;
  height: 36px;
  padding: 0 28px 0 12px;
  box-sizing: border-box;
  border-radius: 18px;
  background: var(--dsw-alias-bg-module-platform);
}

.value {
  font-size: 14px;
  line-height: 22px;
  font-variant-numeric: tabular-nums;
  min-width: 18px;
  text-align: center;
  color: var(--dsw-alias-label-primary);
}

.unit {
  font-size: 13px;
  line-height: 20px;
  color: var(--dsw-alias-label-secondary);
}

.arrows {
  position: absolute;
  right: 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  opacity: 0;
  transition: opacity 120ms ease;
}

.stepper:hover .arrows,
.stepper:focus-within .arrows {
  opacity: 1;
}

.arrow {
  display: grid;
  place-items: center;
  width: 17px;
  height: 12px;
  border: none;
  border-radius: 3px;
  background: color-mix(in srgb, var(--dsw-alias-bg-layer-1) 75%, transparent);
  color: var(--dsw-alias-label-primary);
  cursor: pointer;
  padding: 0;
}

.arrow:hover:not(:disabled) {
  background: var(--dsw-alias-bg-layer-1);
}

.arrow:disabled {
  color: var(--dsw-alias-label-caption);
  cursor: default;
}

.selectWrap {
  position: relative;
  display: inline-flex;
  align-items: center;
  max-width: 260px;
}

.dshSelect {
  appearance: none;
  height: 36px;
  padding: 0 32px 0 14px;
  box-sizing: border-box;
  border-radius: 18px;
  border: 0.5px solid var(--dsw-alias-border-l2);
  background: var(--dsw-alias-bg-module-platform);
  color: var(--dsw-alias-label-primary);
  font-size: 13px;
  cursor: pointer;
  outline: none;
  max-width: 260px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dshSelect:focus {
  border-color: var(--dsw-static-deepseek-450);
}

.selectChevron {
  position: absolute;
  right: 12px;
  pointer-events: none;
  color: var(--dsw-alias-label-tertiary);
}

.inputPill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border-radius: 18px;
  background: var(--dsw-alias-bg-module-platform);
  border: 0.5px solid var(--dsw-alias-border-l2);
  box-sizing: border-box;
}

.numInput {
  width: 60px;
  border: none;
  background: transparent;
  color: var(--dsw-alias-label-primary);
  font-size: 14px;
  line-height: 22px;
  font-variant-numeric: tabular-nums;
  text-align: right;
  outline: none;
}

.numInput::-webkit-inner-spin-button,
.numInput::-webkit-outer-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

.promptArea {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 0 8px;
}

.promptHeader {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.resetButton {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  border: 0.5px solid var(--dsw-alias-border-l2);
  border-radius: 14px;
  background: var(--dsw-alias-bg-module-platform);
  color: var(--dsw-alias-label-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 120ms ease;
}

.resetButton:hover {
  color: var(--dsw-alias-label-primary);
  background: var(--dsw-alias-interactive-bg-hover);
}

.promptTextarea {
  width: 100%;
  box-sizing: border-box;
  border-radius: 16px;
  border: 0.5px solid var(--dsw-alias-border-l2);
  background: var(--dsw-alias-bg-module-platform);
  color: var(--dsw-alias-label-primary);
  font-family: inherit;
  font-size: 13px;
  line-height: 20px;
  padding: 12px 14px;
  resize: vertical;
  min-height: 84px;
  outline: none;
}

.promptTextarea:focus {
  border-color: var(--dsw-static-deepseek-450);
}

.promptTextarea::placeholder {
  color: var(--dsw-alias-label-tertiary);
}
</style>
