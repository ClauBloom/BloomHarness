import { ref } from 'vue';
import { useAgentStore } from '@/stores/agentStore';

export function useWebSocket() {
  const store = useAgentStore();
  const socket = ref<WebSocket | null>(null);

  function connectWs() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    const wsUrl = `${protocol}//${host}/ws/agent`;

    socket.value = new WebSocket(wsUrl);

    socket.value.onopen = () => {
      store.isConnected = true;
      console.log('WebSocket connected to', wsUrl);
    };

    socket.value.onclose = () => {
      store.isConnected = false;
      console.log('WebSocket disconnected');
    };

    socket.value.onerror = (error) => {
      console.error('WebSocket error:', error);
    };

    socket.value.onmessage = (event) => {
      try {
        if (typeof event.data === 'string') {
          const data = JSON.parse(event.data);
          console.log('WS message:', data);
        }
      } catch (err) {
        console.error('Failed to parse WS message:', err);
      }
    };
  }

  function closeWs() {
    if (socket.value) {
      socket.value.close();
      socket.value = null;
    }
  }

  return {
    socket,
    connectWs,
    closeWs,
  };
}
