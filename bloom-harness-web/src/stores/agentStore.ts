import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { SessionMetadata, SessionSnapshot, SessionPhase } from '@/types/session.types';
import { AgentMessage } from '@/types/message.types';
import { useUiStore } from './uiStore';
import { learnSessionTitle } from '@/composables/useSessionTitles';

export const useAgentStore = defineStore('agent', () => {
  const currentSessionId = ref<string | null>(null);
  const currentSession = ref<SessionSnapshot | null>(null);
  const sessionList = ref<SessionMetadata[]>([]);
  const isConnected = ref(false);
  const isSending = ref(false);
  const isCreatingSession = ref(false);
  const errorMessage = ref<string | null>(null);
  const activeThinking = ref<string>('');
  const streamingText = ref<string>('');
  const overridePhase = ref<SessionPhase | null>(null);
  /** Wall-clock start of the running turn (for the status clock); null when idle. */
  const turnStartedAt = ref<number | null>(null);

  /**
   * Hero (blank session) state: the workspace the next session will be created
   * in. `null` means no workspace chosen yet (the composer asks for one).
   */
  const blankSessionCwd = ref<string | null>(null);
  /** Session list fetched at least once (drives the empty-state copy). */
  const sessionsLoaded = ref(false);

  const currentPhase = computed<SessionPhase>(() => {
    if (overridePhase.value) return overridePhase.value;
    return currentSession.value?.phase || 'idle';
  });

  const isRunning = computed(() => currentPhase.value !== 'idle');

  const transcript = computed<AgentMessage[]>(() => currentSession.value?.transcript || []);

  function setSessionList(sessions: SessionMetadata[]) {
    sessionList.value = sessions;
    sessionsLoaded.value = true;
  }

  function setSnapshot(snapshot: SessionSnapshot) {
    currentSession.value = snapshot;
    currentSessionId.value = snapshot.id;
    overridePhase.value = snapshot.phase;
    blankSessionCwd.value = null;
    flushStreaming();
    if (snapshot.phase === 'idle') turnStartedAt.value = null;
    learnSessionTitle(snapshot);
  }

  /** Leave the current session and show the blank hero for `cwd`. */
  function startBlank(cwd: string | null) {
    currentSession.value = null;
    currentSessionId.value = null;
    overridePhase.value = null;
    turnStartedAt.value = null;
    flushStreaming();
    blankSessionCwd.value = cwd;
  }

  function setPhase(phase: SessionPhase) {
    overridePhase.value = phase;
    if (currentSession.value) {
      currentSession.value.phase = phase;
    }
    if (phase === 'idle') {
      turnStartedAt.value = null;
    } else if (turnStartedAt.value === null) {
      turnStartedAt.value = Date.now();
    }
  }

  function appendDelta(kind: 'text' | 'thinking' | 'toolCall', delta: string) {
    if (kind === 'thinking') {
      activeThinking.value += delta;
    } else if (kind === 'text') {
      streamingText.value += delta;
    }
  }

  function flushStreaming() {
    activeThinking.value = '';
    streamingText.value = '';
  }

  function updateItem(item: AgentMessage) {
    if (!currentSession.value) return;
    const idx = currentSession.value.transcript.findIndex((m) => m.id === item.id);
    if (idx >= 0) {
      currentSession.value.transcript[idx] = item;
    } else {
      currentSession.value.transcript.push(item);
    }
    if (currentSession.value.updatedAt !== undefined) currentSession.value.updatedAt = Date.now();
  }

  function setError(msg: string | null) {
    errorMessage.value = msg;
    if (msg) {
      useUiStore().pushToast(msg, 'warning');
      setTimeout(() => {
        if (errorMessage.value === msg) {
          errorMessage.value = null;
        }
      }, 5000);
    }
  }

  return {
    currentSessionId,
    currentSession,
    sessionList,
    sessionsLoaded,
    isConnected,
    isSending,
    isCreatingSession,
    errorMessage,
    currentPhase,
    isRunning,
    turnStartedAt,
    transcript,
    activeThinking,
    streamingText,
    blankSessionCwd,
    setSessionList,
    setSnapshot,
    startBlank,
    setPhase,
    appendDelta,
    flushStreaming,
    updateItem,
    setError,
  };
});
