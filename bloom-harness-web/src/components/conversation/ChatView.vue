<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useAgentStore } from '@/stores/agentStore';
import { useChatFlow } from '@/composables/useChatFlow';
import { IconChevronDown, ICON_STROKE } from '@/components/primitives/icons';
import UserBubble from './UserBubble.vue';
import AssistantMarkdown from './AssistantMarkdown.vue';
import ReasoningRow from './ReasoningRow.vue';
import ToolCallRow from './ToolCallRow.vue';
import TurnTail from './TurnTail.vue';
import TurnStatus from './TurnStatus.vue';
import TurnErrorRow from './TurnErrorRow.vue';

/**
 * dsh ChatView: one flex column of sibling flow items (16px rhythm) inside the
 * conversation scroller, plus the streaming status line and the back-to-bottom
 * control. The scroller itself belongs to ConversationRoot (`scroller` prop).
 */
const props = defineProps<{ scroller: HTMLElement | null }>();

const store = useAgentStore();
const { items } = useChatFlow();

const atBottom = ref(true);
const BOTTOM_THRESHOLD = 60;

function measure() {
  const el = props.scroller;
  if (!el) return;
  atBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < BOTTOM_THRESHOLD;
}

function scrollToBottom(behavior: ScrollBehavior = 'auto') {
  const el = props.scroller;
  if (!el) return;
  el.scrollTo({ top: el.scrollHeight, behavior });
  atBottom.value = true;
}

let raf = 0;
function followBottom() {
  if (!atBottom.value) return;
  cancelAnimationFrame(raf);
  raf = requestAnimationFrame(() => scrollToBottom());
}

watch(
  () => props.scroller,
  (el, old) => {
    old?.removeEventListener('scroll', measure);
    el?.addEventListener('scroll', measure, { passive: true });
    void nextTick(() => {
      scrollToBottom();
    });
  },
  { immediate: true },
);

// Follow new content (streaming deltas, new items) only while pinned to the bottom.
watch(
  () => [items.value.length, store.streamingText.length, store.activeThinking.length],
  () => void nextTick(followBottom),
);

// A session switch always lands at the bottom.
watch(
  () => store.currentSessionId,
  () => {
    atBottom.value = true;
    void nextTick(() => scrollToBottom());
  },
);

let resizeObserver: ResizeObserver | null = null;

onMounted(() => {
  void nextTick(() => scrollToBottom());
  const column = document.querySelector('[data-chat-flow]');
  if (column && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => {
      if (atBottom.value) followBottom();
    });
    resizeObserver.observe(column);
  }
});

onBeforeUnmount(() => {
  props.scroller?.removeEventListener('scroll', measure);
  cancelAnimationFrame(raf);
  resizeObserver?.disconnect();
});

const cwd = computed(() => store.currentSession?.cwd);
</script>

<template>
  <div class="chatView">
    <div class="scroll">
      <div class="column" data-chat-flow>
        <template v-for="item in items" :key="item.key">
          <div v-if="item.kind === 'user'" class="flowItem" data-chat-flow-kind="user" :data-chat-turn="item.turn">
            <UserBubble :message="item.message" :text="item.text" :is-latest="item.isLatest" />
          </div>

          <div
            v-else-if="item.kind === 'assistant-step'"
            class="flowItem"
            data-chat-flow-kind="assistant-step"
            :data-chat-turn="item.turn"
            :data-streaming="item.streaming || undefined"
          >
            <div class="assistant">
              <ReasoningRow v-if="item.thinking" :text="item.thinking" :running="item.streaming && !item.text" />
              <AssistantMarkdown v-if="item.text" :text="item.text" :streaming="item.streaming" />
              <span v-if="item.interrupted" class="stopped">已停止</span>
            </div>
          </div>

          <div v-else-if="item.kind === 'tool-call'" class="flowItem" data-chat-flow-kind="tool-call" :data-chat-turn="item.turn">
            <div class="callRow" :data-chat-call-id="item.call.toolCallId">
              <ToolCallRow :call="item.call" :result="item.result" :running="item.running" :cwd="cwd" />
            </div>
          </div>

          <div v-else-if="item.kind === 'turn-error'" class="flowItem" data-chat-flow-kind="turn-error" :data-chat-turn="item.turn">
            <TurnErrorRow :message="item.message" />
          </div>

          <div v-else-if="item.kind === 'turn-tail'" class="flowItem" data-chat-flow-kind="turn-tail" :data-chat-turn="item.turn">
            <TurnTail :text="item.text" :usage="item.usage" :timestamp="item.timestamp" :is-latest-turn="item.isLatestTurn" />
          </div>
        </template>

        <TurnStatus v-if="store.isRunning" class="flowItem" />
      </div>

      <div v-if="!atBottom" class="toBottomSlot">
        <button type="button" class="toBottom" aria-label="回到底部" @click="scrollToBottom('smooth')">
          <IconChevronDown :size="14" :stroke-width="ICON_STROKE" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chatView {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: auto;
  height: auto;
  flex: 0 0 auto;
}

.scroll {
  display: flex;
  flex-direction: column;
  flex: 0 0 auto;
  min-height: auto;
  overflow: visible;
  padding: 16px calc(var(--dsh-composer-side-clearance, 16px) + 16px) 8px;
  container-type: inline-size;
}

.column {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: var(--dsh-chat-content-width, 748px);
  margin: 0 auto;
  min-width: 0;
}

.column > :not([hidden]):not(.flowItem:empty) ~ :not([hidden]):not(.flowItem:empty) {
  margin-top: var(--dsh-chat-flow-gap, 16px);
}

.flowItem:empty {
  display: none;
}

.flowItem {
  min-width: 0;
}

.assistant {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
  font-size: var(--dsh-content-font-size, 14px);
  line-height: calc(24px + var(--dsh-content-font-delta, 0px));
  color: var(--dsw-alias-label-primary);
}

.stopped {
  align-self: flex-start;
  padding: 0 6px;
  border-radius: 6px;
  background: var(--dsw-alias-interactive-bg-hover);
  color: var(--dsw-alias-label-tertiary);
  font-size: 11px;
  line-height: 18px;
}

.callRow {
  border-radius: 6px;
}

.toBottomSlot {
  position: sticky;
  bottom: calc(var(--dsh-composer-height, 152px) + 16px);
  z-index: 8;
  height: 0;
  display: flex;
  justify-content: flex-end;
  padding-right: max(0px, calc((100% - var(--dsh-chat-content-width, 748px)) / 2));
  pointer-events: none;
}

.toBottom {
  pointer-events: auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  margin-top: -34px;
  border: 0;
  border-radius: 100px;
  corner-shape: round;
  color: var(--dsw-alias-label-primary);
  background: var(--dsw-alias-button-floating-fill);
  --dsw-elevation-stroke-color: var(--dsw-alias-border-l3);
  box-shadow: var(--dsw-elevation-panel);
  cursor: pointer;
}

.toBottom:hover {
  background: var(--dsw-alias-button-floating-hover);
}
</style>
