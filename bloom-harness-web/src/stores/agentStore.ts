import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { SessionMetadata, SessionSnapshot, SessionPhase } from '@/types/session.types';
import { AgentMessage } from '@/types/message.types';

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

  const currentPhase = computed<SessionPhase>(() => {
    if (overridePhase.value) return overridePhase.value;
    return currentSession.value?.phase || 'idle';
  });

  const transcript = computed<AgentMessage[]>(() => currentSession.value?.transcript || []);

  function setSessionList(sessions: SessionMetadata[]) {
    sessionList.value = sessions;
  }

  function setSnapshot(snapshot: SessionSnapshot) {
    currentSession.value = snapshot;
    currentSessionId.value = snapshot.id;
    overridePhase.value = snapshot.phase;
  }

  function setPhase(phase: SessionPhase) {
    overridePhase.value = phase;
    if (currentSession.value) {
      currentSession.value.phase = phase;
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
    const idx = currentSession.value.transcript.findIndex(m => m.id === item.id);
    if (idx >= 0) {
      currentSession.value.transcript[idx] = item;
    } else {
      currentSession.value.transcript.push(item);
    }
  }

  function setError(msg: string | null) {
    errorMessage.value = msg;
    if (msg) {
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
    isConnected,
    isSending,
    isCreatingSession,
    errorMessage,
    currentPhase,
    transcript,
    activeThinking,
    streamingText,
    setSessionList,
    setSnapshot,
    setPhase,
    appendDelta,
    flushStreaming,
    updateItem,
    setError,
  };
});
