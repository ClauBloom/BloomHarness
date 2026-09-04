import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
export const useAgentStore = defineStore('agent', () => {
    const currentSessionId = ref(null);
    const currentSession = ref(null);
    const sessionList = ref([]);
    const isConnected = ref(false);
    const isSending = ref(false);
    const isCreatingSession = ref(false);
    const errorMessage = ref(null);
    const activeThinking = ref('');
    const streamingText = ref('');
    const overridePhase = ref(null);
    const currentPhase = computed(() => {
        if (overridePhase.value)
            return overridePhase.value;
        return currentSession.value?.phase || 'idle';
    });
    const transcript = computed(() => currentSession.value?.transcript || []);
    function setSessionList(sessions) {
        sessionList.value = sessions;
    }
    function setSnapshot(snapshot) {
        currentSession.value = snapshot;
        currentSessionId.value = snapshot.id;
        overridePhase.value = snapshot.phase;
    }
    function setPhase(phase) {
        overridePhase.value = phase;
        if (currentSession.value) {
            currentSession.value.phase = phase;
        }
    }
    function appendDelta(kind, delta) {
        if (kind === 'thinking') {
            activeThinking.value += delta;
        }
        else if (kind === 'text') {
            streamingText.value += delta;
        }
    }
    function flushStreaming() {
        activeThinking.value = '';
        streamingText.value = '';
    }
    function updateItem(item) {
        if (!currentSession.value)
            return;
        const idx = currentSession.value.transcript.findIndex(m => m.id === item.id);
        if (idx >= 0) {
            currentSession.value.transcript[idx] = item;
        }
        else {
            currentSession.value.transcript.push(item);
        }
    }
    function setError(msg) {
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
