<script setup lang="ts">
import { onBeforeUnmount, watch, ref, nextTick } from 'vue';
import { IconClose, ICON_STROKE } from './icons';

/**
 * dsh Modal: body-portaled mask + centred dialog card (r24, layer-2 fill,
 * prominent elevation). Escape and mask click close unless `blocking`.
 */
const props = withDefaults(
  defineProps<{
    open: boolean;
    title: string;
    description?: string;
    /** CSS width of the dialog card; defaults to dsh's `min(380px, 100%)`. */
    width?: string;
    /** Render only the children inside the card (no header/close). */
    headless?: boolean;
    /** Ignore Escape / mask dismissal (onboarding-style). */
    blocking?: boolean;
    contentClass?: string;
  }>(),
  { description: undefined, width: undefined, headless: false, blocking: false, contentClass: undefined },
);

const emit = defineEmits<{ close: [] }>();

const closeRef = ref<HTMLButtonElement | null>(null);

function onKeyDown(event: KeyboardEvent) {
  if (event.key === 'Escape' && !props.blocking) {
    event.stopPropagation();
    emit('close');
  }
}

watch(
  () => props.open,
  async (open) => {
    if (open) {
      document.addEventListener('keydown', onKeyDown, true);
      await nextTick();
      closeRef.value?.focus();
    } else {
      document.removeEventListener('keydown', onKeyDown, true);
    }
  },
  { immediate: true },
);

onBeforeUnmount(() => document.removeEventListener('keydown', onKeyDown, true));

function onMask() {
  if (!props.blocking) emit('close');
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="modalRoot" role="presentation">
      <div class="mask" aria-hidden="true" @click="onMask" />
      <div
        :class="['dialog', contentClass]"
        :style="width ? { width } : undefined"
        role="dialog"
        aria-modal="true"
        :aria-label="title"
      >
        <template v-if="headless">
          <slot />
        </template>
        <template v-else>
          <div class="content">
            <div class="header">
              <h2 class="title">{{ title }}</h2>
              <button ref="closeRef" type="button" class="close" aria-label="关闭" @click="emit('close')">
                <IconClose :size="14" :stroke-width="ICON_STROKE" />
              </button>
            </div>
            <p v-if="description" class="description">{{ description }}</p>
            <div v-if="$slots.default" class="body"><slot /></div>
          </div>
          <div v-if="$slots.footer" class="footer"><slot name="footer" /></div>
        </template>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modalRoot {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.mask {
  position: absolute;
  inset: 0;
  background: var(--dsw-alias-bg-mask-1);
  backdrop-filter: var(--dsw-mask-blur);
}

.dialog {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
  width: min(380px, 100%);
  max-width: 100%;
  max-height: 100%;
  padding: 0 0 24px;
  overflow: hidden;
  border: 0;
  border-radius: 24px;
  background: var(--dsw-alias-bg-layer-2);
  box-shadow: var(--dsw-elevation-prominent);
  --dsh-scrollbar-thumb: var(--dsw-alias-scrollbar-bg-l2);
  --dsh-scrollbar-thumb-hover: var(--dsw-alias-scrollbar-hover-l2);
}

.content {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-height: 0;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 22px 14px 12px 24px;
}

.title {
  margin: 0;
  font-size: 16px;
  line-height: 24px;
  font-weight: 500;
  color: var(--dsw-alias-label-primary);
}

.close {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  color: var(--dsw-alias-label-secondary);
}

.close:hover {
  background: var(--dsw-alias-interactive-bg-hover);
}

.description {
  margin: 0;
  padding: 0 24px;
  font-size: 14px;
  line-height: 22px;
  font-weight: 400;
  color: var(--dsw-alias-label-primary);
}

.body {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  margin-top: 20px;
  padding: 0 24px;
  overflow-y: auto;
}

.footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 0 24px;
}
</style>
