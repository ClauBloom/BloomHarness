<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { useAgentStore } from '@/stores/agentStore';
import DsTooltip from '@/components/primitives/DsTooltip.vue';
import { IconPlus, ICON_STROKE } from '@/components/primitives/icons';
import ModelSelect from './ModelSelect.vue';

/**
 * dsh InputBar: the composer card (r22, input surface, soft elevation) with the
 * auto-growing draft, the left tool circle, the model chip and the 34px primary
 * send / stop circle. Enter sends, Shift+Enter inserts a newline.
 */
const props = withDefaults(
  defineProps<{
    variant: 'hero' | 'composer';
    disabled?: boolean;
    placeholder?: string;
    /** Draft is scoped to the session it belongs to. */
    draftKey: string;
  }>(),
  { disabled: false, placeholder: undefined },
);

const emit = defineEmits<{ send: [text: string]; stop: []; steer: [text: string] }>();

const store = useAgentStore();

const drafts = new Map<string, string>();
const draft = ref('');
const textarea = ref<HTMLTextAreaElement | null>(null);

watch(
  () => props.draftKey,
  (key, old) => {
    if (old !== undefined) drafts.set(old, draft.value);
    draft.value = drafts.get(key) ?? '';
    void nextTick(autoGrow);
  },
  { immediate: true },
);

const running = computed(() => store.isRunning);
const empty = computed(() => draft.value.trim().length === 0);
const hasModel = ref(true);
function onModelChange(model: unknown) {
  hasModel.value = !!model;
}

// 当正在运行且没有输入草稿时，主按钮为停止；若已输入草稿，主按钮为引导 (Steer)
const isSteerMode = computed(() => running.value && !!store.currentSessionId && !empty.value);
const primaryStops = computed(() => running.value && !!store.currentSessionId && empty.value);

const primaryDisabled = computed(() => {
  if (primaryStops.value) return false;
  if (isSteerMode.value) return false;
  return empty.value || props.disabled || !hasModel.value;
});

const primaryTooltip = computed(() => {
  if (isSteerMode.value) return '引导当前智能体 (Enter 提交)';
  if (primaryStops.value) return '停止当前运行';
  if (!hasModel.value) return '请先在下方配置并选择模型';
  return '发送';
});

const placeholderText = computed(() => {
  if (props.placeholder) return props.placeholder;
  if (running.value && store.currentSessionId) return '输入实时引导建议（Enter 注入）…';
  if (props.variant === 'hero') return '描述你想要构建的内容…';
  return '发送消息…';
});

function autoGrow() {
  const el = textarea.value;
  if (!el) return;
  el.style.height = '0px';
  el.style.height = `${el.scrollHeight}px`;
}

watch(draft, () => void nextTick(autoGrow));

function submit() {
  if (isSteerMode.value) {
    const text = draft.value;
    draft.value = '';
    drafts.set(props.draftKey, '');
    void nextTick(autoGrow);
    emit('steer', text);
    return;
  }
  if (primaryStops.value) {
    emit('stop');
    return;
  }
  if (primaryDisabled.value) return;
  const text = draft.value;
  draft.value = '';
  drafts.set(props.draftKey, '');
  void nextTick(autoGrow);
  emit('send', text);
}

function onKeyDown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return;
  event.preventDefault();
  if (running.value) {
    if (isSteerMode.value) submit();
    return;
  }
  submit();
}

function focus() {
  textarea.value?.focus({ preventScroll: true });
}

defineExpose({ focus });
</script>

<template>
  <div :class="['inputBar', { hero: variant === 'hero' }]">
    <div class="card" data-composer-card>
      <div class="scroll" data-input-scroll>
        <div class="grow">
          <textarea
            ref="textarea"
            v-model="draft"
            :class="['input', { inputDisabled: disabled }]"
            :placeholder="placeholderText"
            :disabled="disabled"
            rows="1"
            aria-label="消息输入"
            @keydown="onKeyDown"
          />
        </div>
      </div>
      <div class="row">
        <div class="tools">
          <DsTooltip label="附件功能暂未开放" side="top">
            <button type="button" class="add" aria-label="附件" disabled>
              <IconPlus :size="14" :stroke-width="ICON_STROKE" />
            </button>
          </DsTooltip>
        </div>
        <div class="trailing">
          <ModelSelect :locked="disabled" @change="onModelChange" />
          <DsTooltip v-if="isSteerMode" label="停止当前运行" side="top">
            <button
              type="button"
              class="stopSecondary"
              aria-label="停止当前运行"
              @mousedown.prevent
              @click="emit('stop')"
            >
              <svg width="12" height="12" viewBox="0 0 16 16" aria-hidden="true">
                <rect x="3" y="3" width="10" height="10" rx="2.5" fill="currentColor" />
              </svg>
            </button>
          </DsTooltip>
          <DsTooltip :label="primaryTooltip" side="top">
            <button
              type="button"
              :class="['primary', { isSteer: isSteerMode, isStop: primaryStops }]"
              :aria-label="primaryTooltip"
              :disabled="primaryDisabled"
              @mousedown.prevent
              @click="submit"
            >
              <svg v-if="primaryStops" width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
                <rect x="3" y="3" width="10" height="10" rx="3" fill="currentColor" />
              </svg>
              <svg v-else-if="isSteerMode" width="15" height="15" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                <path d="M8 2.5L13.5 13.5L8 10.5L2.5 13.5L8 2.5Z" fill="currentColor" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round" />
              </svg>
              <svg v-else width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
                <path
                  d="M8.3125 0.980183C8.66767 1.0531 8.97902 1.20418 9.2627 1.43233C9.48724 1.61297 9.73029 1.85793 9.97949 2.10714L14.707 6.83468L13.293 8.24874L9 3.95577V15.0417H7V3.95577L2.70703 8.24874L1.29297 6.83468L6.02051 2.10714C6.26971 1.85793 6.51277 1.61297 6.7373 1.43233C6.97662 1.23986 7.28445 1.04402 7.6875 0.980183C7.8973 0.947006 8.1031 0.95516 8.3125 0.980183Z"
                  fill="currentColor"
                />
              </svg>
            </button>
          </DsTooltip>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.inputBar {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 var(--dsh-composer-side-clearance, 16px) 8px;
}

.hero {
  padding: 0 var(--dsh-composer-side-clearance, 16px);
}

.card {
  box-sizing: border-box;
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
  max-width: var(--dsh-composer-card-max-width);
  padding-top: 10px;
  border: 0;
  --dsw-elevation-stroke-color: var(--dsw-alias-border-l2);
  border-radius: 22px;
  background: var(--dsw-specific-input-major);
  box-shadow: var(--dsw-elevation-soft);
  font-size: var(--dsh-content-font-size, 14px);
  line-height: calc(24px + var(--dsh-content-font-delta, 0px));
  --dsh-scrollbar-thumb: var(--dsw-alias-scrollbar-bg-l2);
  --dsh-scrollbar-thumb-hover: var(--dsw-alias-scrollbar-hover-l2);
}

.scroll {
  max-height: var(--dsh-composer-text-max-height, 336px);
  overflow-y: auto;
  margin-right: 4px;
}

.scroll::-webkit-scrollbar-track {
  margin-top: 8px;
}

.grow {
  position: relative;
  display: flex;
}

.input {
  display: block;
  box-sizing: border-box;
  width: 100%;
  min-height: calc(28px + var(--dsh-content-font-delta, 0px));
  padding: 4px 8px 0 16px;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  font-family: var(--dsw-font-family);
  font-size: inherit;
  line-height: inherit;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
  overflow: hidden;
  color: var(--dsw-alias-label-primary);
  caret-color: var(--dsw-alias-state-business-primary);
}

.input::placeholder {
  color: var(--dsw-alias-label-tertiary);
}

.inputDisabled {
  color: var(--dsw-alias-label-tertiary);
  cursor: not-allowed;
}

.hero .input {
  min-height: calc(52px + var(--dsh-content-font-delta, 0px));
}

.row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 2px 8px 6px;
  min-width: 0;
  container-type: inline-size;
}

.tools,
.trailing {
  display: flex;
  align-items: center;
  min-width: 0;
}

.tools {
  gap: 16px;
}

.trailing {
  flex: none;
  margin-left: auto;
  gap: 12px;
}

.add {
  display: grid;
  place-items: center;
  flex: none;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 999px;
  corner-shape: round;
  background: var(--dsw-specific-selector);
  color: var(--dsw-alias-label-primary);
  cursor: pointer;
}

.add:hover:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-hover-solid);
}

.add:disabled {
  opacity: 0.5;
  cursor: default;
}

.primary {
  display: grid;
  place-items: center;
  flex: none;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 999px;
  corner-shape: round;
  background: var(--dsw-alias-button-info-fill);
  color: var(--dsw-static-neutral-bluish-00, #ffffff);
  cursor: pointer;
  transition: background-color 100ms ease;
  transform: translateY(-2px);
}

.primary:hover:not(:disabled) {
  background: var(--dsw-alias-button-info-hover);
}

.primary:disabled {
  opacity: 0.4;
  cursor: default;
}

.primary.isSteer {
  background: var(--dsw-static-deepseek-500);
}

.primary.isSteer:hover {
  background: var(--dsw-static-deepseek-450);
}

.stopSecondary {
  display: grid;
  place-items: center;
  flex: none;
  width: 30px;
  height: 30px;
  border: 0.5px solid var(--dsw-alias-border-l3);
  border-radius: 999px;
  background: var(--dsw-alias-bg-module-platform);
  color: var(--dsw-alias-label-secondary);
  cursor: pointer;
  transition: all 120ms ease;
  transform: translateY(-2px);
}

.stopSecondary:hover {
  background: color-mix(in srgb, var(--dsw-static-red-500) 15%, transparent);
  color: var(--dsw-static-red-500);
  border-color: color-mix(in srgb, var(--dsw-static-red-500) 40%, transparent);
}
</style>
