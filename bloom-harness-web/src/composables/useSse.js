import { useAgentStore } from '@/stores/agentStore';
// 模块级单例状态：确保无论多少组件调用 useSession/useSse，全局有且仅维持一条活跃的 SSE 连接
let globalEventSource = null;
let activeSessionId = null;
export function useSse() {
    const store = useAgentStore();
    function connectSse(sessionId) {
        if (!sessionId)
            return;
        // 如果已经与当前会话建立了 SSE 监听且连接正常，避免重复创建连接
        if (globalEventSource && activeSessionId === sessionId) {
            if (globalEventSource.readyState === EventSource.OPEN || globalEventSource.readyState === EventSource.CONNECTING) {
                console.debug(`[useSse] 已存在对会话 ${sessionId} 的活跃连接，忽略重复建立请求`);
                return;
            }
        }
        // 存在旧会话连接或连接处于非正常状态时，先断开清理
        closeSse();
        activeSessionId = sessionId;
        const es = new EventSource(`/api/stream/${sessionId}`);
        globalEventSource = es;
        es.onopen = () => {
            // 防止已过期的连接回调
            if (globalEventSource !== es)
                return;
            store.isConnected = true;
        };
        es.onmessage = (event) => {
            if (globalEventSource !== es)
                return;
            try {
                const data = JSON.parse(event.data);
                if (data.type === 'chunk' && data.payload) {
                    const payload = data.payload;
                    if (payload.type === 'assistant_delta') {
                        store.appendDelta(payload.kind, payload.delta);
                    }
                    else if (payload.type === 'item_started' || payload.type === 'item_updated' || payload.type === 'item_finished') {
                        store.updateItem(payload.item);
                    }
                }
                else if (data.type === 'error') {
                    console.error('[SSE Error Event]', data);
                    store.setError(data.message || '模型执行过程出现异常');
                    store.flushStreaming();
                    store.setPhase('idle');
                    closeSse();
                }
                else if (data.type === 'end') {
                    store.flushStreaming();
                    store.setPhase('idle');
                    // 收到服务端终结标记后，主动关闭连接，避免 W3C 规范触发浏览器在 Flux 结束后 3s 无效自动重连
                    closeSse();
                    // 同步最新全量状态
                    fetch(`/api/sessions/${sessionId}`)
                        .then(r => r.ok ? r.json() : null)
                        .then(snapshot => {
                        if (snapshot)
                            store.setSnapshot(snapshot);
                    })
                        .catch(() => { });
                }
            }
            catch (err) {
                console.error('Failed parsing SSE payload:', err);
            }
        };
        es.onerror = (err) => {
            if (globalEventSource !== es)
                return;
            console.warn('SSE disconnected or stream closed:', err);
            store.isConnected = false;
            store.setPhase('idle');
            closeSse();
        };
    }
    function closeSse() {
        if (globalEventSource) {
            try {
                globalEventSource.close();
            }
            catch (e) { }
            globalEventSource = null;
        }
        activeSessionId = null;
        store.isConnected = false;
    }
    return { connectSse, closeSse };
}
