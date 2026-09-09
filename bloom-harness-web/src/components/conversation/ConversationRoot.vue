<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useAgentStore } from '@/stores/agentStore';
import { useUiStore } from '@/stores/uiStore';
import { useSession } from '@/composables/useSession';
import { useSessionTitles } from '@/composables/useSessionTitles';
import DsTooltip from '@/components/primitives/DsTooltip.vue';
import { IconFolderClose, IconWarning, ICON_STROKE } from '@/components/primitives/icons';
import { basename } from '@/utils/format';
import { useProviders } from '@/composables/useProviders';
import ChatView from './ChatView.vue';
import HeroShell from './HeroShell.vue';
import WorkspaceChip from './WorkspaceChip.vue';
import InputBar from './InputBar.vue';
import StatsLine from './StatsLine.vue';

/**
 * dsh ConversationRoot: the conversation column. Owns the shared width axis
 * (`--dsh-chat-content-width` = clamp(680px, 64% of the column, 920px)), the
 * session header, the scroll body and the composer seat (centred in the hero
 * phase, sticky at the bottom in the active phase).
 */
const store = useAgentStore();
const ui = useUiStore();
const { sendPrompt, abortSession, steerSession } = useSession();
const { titles, sessionTitle } = useSessionTitles();

const rootRef = ref<HTMLElement | null>(null);
const scroller = ref<HTMLElement | null>(null);
const seatRef = ref<HTMLElement | null>(null);
const inputBar = ref<InstanceType<typeof InputBar> | null>(null);

const phase = computed<'hero' | 'active'>(() => (store.currentSessionId ? 'active' : 'hero'));
const title = computed(() => {
  const session = store.currentSession;
  if (!session) return '新会话';
  // `titles` is read so the header follows a title learned after the snapshot arrived.
  return titles[session.id] ?? sessionTitle(session);
});
const cwd = computed(() => store.currentSession?.cwd ?? '');
const draftKey = computed(() => store.currentSessionId ?? `blank:${store.blankSessionCwd ?? ''}`);
const heroNeedsWorkspace = computed(() => phase.value === 'hero' && !store.blankSessionCwd);

const { providers, loaded: providersLoaded, ensureLoaded: ensureProvidersLoaded } = useProviders();
onMounted(() => void ensureProvidersLoaded());
const hasNoModels = computed(() => providersLoaded.value && !providers.value.some((p) => p.models && p.models.length > 0));

const columnWidth = ref(0);
const composerHeight = ref(152);

let rootObserver: ResizeObserver | null = null;
let seatObserver: ResizeObserver | null = null;

onMounted(() => {
  rootObserver = new ResizeObserver((entries) => {
    for (const entry of entries) columnWidth.value = entry.contentRect.width;
  });
  if (rootRef.value) rootObserver.observe(rootRef.value);
  seatObserver = new ResizeObserver((entries) => {
    for (const entry of entries) composerHeight.value = entry.contentRect.height;
  });
  if (seatRef.value) seatObserver.observe(seatRef.value);
});

watch(seatRef, (el, old) => {
  if (old) seatObserver?.unobserve(old);
  if (el) seatObserver?.observe(el);
});

onBeforeUnmount(() => {
  rootObserver?.disconnect();
  seatObserver?.disconnect();
});

const rootStyle = computed(() => ({
  '--dsh-conversation-column-width': `${columnWidth.value}px`,
}));

const scrollStyle = computed(() => ({
  '--dsh-composer-height': `${composerHeight.value}px`,
}));

watch(
  [title, phase],
  () => {
    document.title = phase.value === 'active' ? `${title.value} — BloomHarness` : 'BloomHarness';
  },
  { immediate: true },
);

function onSend(text: string) {
  void sendPrompt(store.currentSessionId, text);
}

function onStop() {
  void abortSession();
}

function onSteer(text: string) {
  if (store.currentSessionId) {
    void steerSession(store.currentSessionId, text);
  }
}

function onHeroCardClick() {
  if (heroNeedsWorkspace.value) ui.openWorkspaceDialog('hero');
}

watch(
  () => store.currentSessionId,
  () => {
    setTimeout(() => inputBar.value?.focus(), 50);
  },
);
</script>

<template>
  <div ref="rootRef" class="conversation" :data-phase="phase" :style="rootStyle">
    <header v-if="phase === 'active'" class="header">
      <div class="titleRow">
        <div class="titleCluster">
          <nav class="crumbs" aria-label="会话层级">
            <span class="crumbSeg">
              <button type="button" class="crumb crumbCurrent" disabled>{{ title }}</button>
            </span>
          </nav>
        </div>
        <div class="headerActions">
          <DsTooltip :label="cwd || '切换工作区'" side="bottom" :delay="400">
            <button type="button" class="cwdButton" aria-label="切换工作区" @click="ui.openWorkspaceDialog('session-cwd')">
              <IconFolderClose :size="14" :stroke-width="ICON_STROKE" />
              <span class="cwdLabel">{{ basename(cwd) || '选择工作区' }}</span>
            </button>
          </DsTooltip>
        </div>
      </div>
    </header>

    <div class="body">
      <div ref="scroller" class="scrollBody" data-conversation-scroll :style="scrollStyle">
        <div v-if="phase === 'active'" class="viewArea">
          <ChatView :scroller="scroller" />
        </div>

        <div ref="seatRef" class="composerSeat" data-composer-seat>
          <div :class="['composerStack', { composerHero: phase === 'hero' }]" @click="onHeroCardClick">
            <template v-if="phase === 'hero'">
              <HeroShell />
              <div class="heroWorkspaceRow">
                <WorkspaceChip />
              </div>
            </template>
            <InputBar
              ref="inputBar"
              :variant="phase === 'hero' ? 'hero' : 'composer'"
              :draft-key="draftKey"
              :disabled="heroNeedsWorkspace"
              :placeholder="heroNeedsWorkspace ? '选择一个工作区开始' : undefined"
              @send="onSend"
              @stop="onStop"
              @steer="onSteer"
            />
            <div v-if="hasNoModels" class="noModelBanner" role="button" tabindex="0" @click.stop="ui.openSettings('models')">
              <IconWarning :size="14" :stroke-width="ICON_STROKE" />
              <span>尚未配置可用模型，前往设置配置服务商与模型 →</span>
            </div>
            <StatsLine v-if="phase === 'active'" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.conversation {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
  background: var(--dsw-alias-bg-base);
  --dsh-chat-content-width: clamp(680px, calc(var(--dsh-conversation-column-width, 0px) * 0.64), 920px);
  --dsh-composer-card-max-width: calc(var(--dsh-chat-content-width) + 32px);
  --dsh-composer-side-clearance: 16px;
  --dsh-composer-dock-inset: 8px;
}

.header {
  position: relative;
  flex: none;
  padding: 12px 28px 0 20px;
  border-bottom: 1px solid transparent;
}

.header::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: 1px;
  left: 0;
  z-index: 0;
  height: 0.5px;
  background: var(--dsw-alias-border-l3);
  pointer-events: none;
}

.titleRow {
  display: flex;
  align-items: center;
  gap: 0;
  min-height: 32px;
  padding-bottom: 12px;
}

.titleCluster {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.crumbs {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
}

.crumbSeg {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.crumb {
  max-width: 420px;
  overflow: hidden;
  padding: 4px 8px;
  border: none;
  border-radius: 12px;
  background: transparent;
  font-size: 14px;
  line-height: 20px;
  color: var(--dsw-alias-label-tertiary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.crumbCurrent {
  font-weight: 500;
  color: var(--dsw-alias-label-primary);
  cursor: default;
}

.headerActions {
  display: flex;
  flex: none;
  align-items: center;
  gap: 8px;
}

.cwdButton {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 260px;
  height: 28px;
  padding: 0 10px;
  border: none;
  border-radius: 12px;
  background: transparent;
  color: var(--dsw-alias-label-tertiary);
  font-size: 13px;
  line-height: 20px;
  cursor: pointer;
}

.cwdButton:hover {
  background: var(--dsw-alias-interactive-bg-hover);
  color: var(--dsw-alias-label-primary);
}

.cwdLabel {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.body {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.scrollBody {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow-y: auto;
  scrollbar-gutter: stable;
}

.viewArea {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.conversation[data-phase='active'] .viewArea {
  flex: 1 0 auto;
  min-height: auto;
}

.composerSeat {
  display: flex;
  flex: none;
  flex-direction: column;
  --dsh-composer-text-max-height: 336px;
}

.composerStack {
  --dsh-composer-stack-gap: 6px;
  display: flex;
  flex-direction: column;
  gap: var(--dsh-composer-stack-gap);
}

.conversation[data-phase='active'] {
  overflow: hidden;
}

.conversation[data-phase='active'] .composerSeat {
  position: sticky;
  bottom: 0;
  z-index: 7;
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--dsw-alias-bg-base) 0%, transparent) 0px,
    var(--dsw-alias-bg-base) 36px
  );
}

.composerHero {
  align-self: center;
  gap: 8px;
  padding-bottom: 32px;
  width: min(calc(var(--dsh-composer-card-max-width) + 2 * var(--dsh-composer-side-clearance)), 100%);
  z-index: 1;
}

.heroWorkspaceRow {
  display: flex;
  align-items: center;
  gap: 2px;
  min-width: 0;
  margin-top: 4px;
  padding-left: 20px;
}

.conversation[data-phase='hero'] .scrollBody {
  justify-content: center;
  overflow-y: auto;
}

.noModelBanner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  max-width: var(--dsh-composer-card-max-width);
  margin: 4px auto 0;
  padding: 6px 12px;
  box-sizing: border-box;
  border-radius: 10px;
  background: color-mix(in srgb, var(--dsw-alias-state-warn-label) 12%, transparent);
  color: var(--dsw-alias-state-warn-label);
  font-size: 12px;
  line-height: 18px;
  cursor: pointer;
  user-select: none;
}

.noModelBanner:hover {
  background: color-mix(in srgb, var(--dsw-alias-state-warn-label) 20%, transparent);
}
</style>
