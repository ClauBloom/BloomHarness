/// <reference types="../../node_modules/.vue-global-types/vue_3.5_0_0_0.d.ts" />
import { ref, watch, nextTick, computed, onMounted } from 'vue';
import { useAgentStore } from '@/stores/agentStore';
import { useSession } from '@/composables/useSession';
import MessageBubble from './MessageBubble.vue';
import { Send, Square, Sparkles, Loader2, AlertTriangle, FolderGit2, Cpu } from 'lucide-vue-next';
const emit = defineEmits();
const store = useAgentStore();
const { sendPrompt, abortSession, createSession } = useSession();
const inputPrompt = ref('');
const messagesContainer = ref(null);
const textareaRef = ref(null);
const providers = ref([]);
const selectedModelKey = ref(localStorage.getItem('bloom_selected_model') || '');
onMounted(async () => {
    await loadAvailableModels();
    // Listen for provider updates from SettingsModal
    window.addEventListener('bloom:providers-updated', loadAvailableModels);
});
async function loadAvailableModels() {
    try {
        const res = await fetch('/api/config/providers');
        if (res.ok) {
            providers.value = await res.json();
            // Auto select saved model or first available model
            const saved = localStorage.getItem('bloom_selected_model');
            if (saved && modelOptions.value.some(opt => opt.value === saved)) {
                selectedModelKey.value = saved;
            }
            else if (modelOptions.value.length > 0) {
                selectedModelKey.value = modelOptions.value[0].value;
                localStorage.setItem('bloom_selected_model', selectedModelKey.value);
            }
            if (store.currentSessionId && selectedModelKey.value) {
                handleModelChange();
            }
        }
    }
    catch (e) {
        console.warn('Failed loading providers for selector:', e);
    }
}
async function handleModelChange() {
    if (!selectedModelKey.value)
        return;
    localStorage.setItem('bloom_selected_model', selectedModelKey.value);
    if (store.currentSessionId && selectedModelKey.value.includes(':')) {
        const [provider, ...rest] = selectedModelKey.value.split(':');
        const modelId = rest.join(':');
        try {
            await fetch(`/api/sessions/${store.currentSessionId}/model`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ provider, id: modelId }),
            });
        }
        catch (e) {
            console.warn('Failed syncing model to session:', e);
        }
    }
}
const modelOptions = computed(() => {
    const options = [];
    providers.value.forEach(p => {
        (p.models || []).forEach(m => {
            options.push({
                label: `${p.name || p.providerId} / ${m}`,
                value: `${p.providerId}:${m}`,
                providerId: p.providerId,
                modelId: m,
            });
        });
    });
    return options;
});
function scrollToBottom() {
    nextTick(() => {
        if (messagesContainer.value) {
            messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
        }
    });
}
watch(() => [store.transcript.length, store.streamingText, store.activeThinking], () => {
    scrollToBottom();
});
const isRunning = computed(() => store.currentPhase !== 'idle');
async function handleSendOrAbort() {
    if (isRunning.value) {
        await abortSession();
        return;
    }
    const prompt = inputPrompt.value.trim();
    if (!prompt)
        return;
    inputPrompt.value = '';
    adjustTextareaHeight();
    await sendPrompt(store.currentSessionId, prompt);
}
function handleKeydown(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        handleSendOrAbort();
    }
}
function adjustTextareaHeight() {
    nextTick(() => {
        if (textareaRef.value) {
            textareaRef.value.style.height = 'auto';
            textareaRef.value.style.height = `${Math.min(textareaRef.value.scrollHeight, 180)}px`;
        }
    });
}
function applyQuickPrompt(promptText) {
    inputPrompt.value = promptText;
    adjustTextareaHeight();
    handleSendOrAbort();
}
debugger; /* PartiallyEnd: #3632/scriptSetup.vue */
const __VLS_ctx = {};
let __VLS_components;
let __VLS_directives;
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex flex-col h-full bg-gray-950 text-gray-100" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.header, __VLS_intrinsicElements.header)({
    ...{ class: "h-14 px-6 border-b border-gray-800 bg-gray-900/60 backdrop-blur flex items-center justify-between shrink-0" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex items-center gap-3 min-w-0" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.h1, __VLS_intrinsicElements.h1)({
    ...{ class: "text-sm font-semibold text-gray-200 truncate" },
});
(__VLS_ctx.store.currentSession?.name || __VLS_ctx.store.currentSessionId || '未选择会话');
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-medium" },
    ...{ class: (__VLS_ctx.isRunning ? 'bg-amber-500/10 text-amber-400 border border-amber-500/30' : 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30') },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
    ...{ class: "w-1.5 h-1.5 rounded-full" },
    ...{ class: (__VLS_ctx.isRunning ? 'bg-amber-400 animate-ping' : 'bg-emerald-400') },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
(__VLS_ctx.isRunning ? 'Agent 正在执行...' : '就绪');
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex items-center gap-4 text-xs text-gray-400" },
});
if (__VLS_ctx.store.currentSession?.cwd) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.store.currentSession?.cwd))
                    return;
                __VLS_ctx.emit('openWorkspace');
            } },
        title: "点击更换当前工作区目录",
        ...{ class: "hidden sm:flex items-center gap-1.5 text-gray-400 hover:text-purple-300 px-2 py-1 rounded-lg hover:bg-gray-800 transition" },
    });
    const __VLS_0 = {}.FolderGit2;
    /** @type {[typeof __VLS_components.FolderGit2, ]} */ ;
    // @ts-ignore
    const __VLS_1 = __VLS_asFunctionalComponent(__VLS_0, new __VLS_0({
        ...{ class: "w-3.5 h-3.5 text-purple-400" },
    }));
    const __VLS_2 = __VLS_1({
        ...{ class: "w-3.5 h-3.5 text-purple-400" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_1));
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
        ...{ class: "max-w-[200px] truncate font-mono text-[11px]" },
    });
    (__VLS_ctx.store.currentSession.cwd);
}
if (__VLS_ctx.store.errorMessage) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "px-6 py-2 bg-rose-950/80 border-b border-rose-800 text-rose-200 text-xs flex items-center justify-between animate-fadeIn" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "flex items-center gap-2" },
    });
    const __VLS_4 = {}.AlertTriangle;
    /** @type {[typeof __VLS_components.AlertTriangle, ]} */ ;
    // @ts-ignore
    const __VLS_5 = __VLS_asFunctionalComponent(__VLS_4, new __VLS_4({
        ...{ class: "w-4 h-4 text-rose-400 shrink-0" },
    }));
    const __VLS_6 = __VLS_5({
        ...{ class: "w-4 h-4 text-rose-400 shrink-0" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_5));
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
    (__VLS_ctx.store.errorMessage);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.store.errorMessage))
                    return;
                __VLS_ctx.store.setError(null);
            } },
        ...{ class: "text-rose-300 hover:text-white text-xs underline ml-2" },
    });
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ref: "messagesContainer",
    ...{ class: "flex-1 overflow-y-auto px-6 py-6 space-y-4" },
});
/** @type {typeof __VLS_ctx.messagesContainer} */ ;
if (__VLS_ctx.store.transcript.length === 0) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "h-full flex flex-col items-center justify-center text-center max-w-lg mx-auto py-12 space-y-6" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "w-16 h-16 rounded-2xl bg-purple-900/30 border border-purple-500/30 flex items-center justify-center shadow-lg shadow-purple-900/20" },
    });
    const __VLS_8 = {}.Sparkles;
    /** @type {[typeof __VLS_components.Sparkles, ]} */ ;
    // @ts-ignore
    const __VLS_9 = __VLS_asFunctionalComponent(__VLS_8, new __VLS_8({
        ...{ class: "w-8 h-8 text-purple-400" },
    }));
    const __VLS_10 = __VLS_9({
        ...{ class: "w-8 h-8 text-purple-400" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_9));
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.h2, __VLS_intrinsicElements.h2)({
        ...{ class: "text-lg font-bold text-gray-200" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({
        ...{ class: "text-sm text-gray-400 mt-1" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "grid grid-cols-1 sm:grid-cols-2 gap-2.5 w-full text-left text-xs" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.store.transcript.length === 0))
                    return;
                __VLS_ctx.applyQuickPrompt('检查当前项目目录结构并给出概要说明');
            } },
        ...{ class: "p-3 rounded-xl bg-gray-900/80 border border-gray-800 hover:border-purple-500/50 hover:bg-gray-800/80 transition text-gray-300 flex flex-col gap-1" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
        ...{ class: "font-semibold text-purple-300" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
        ...{ class: "text-gray-500" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.store.transcript.length === 0))
                    return;
                __VLS_ctx.applyQuickPrompt('执行测试并分析是否有报错');
            } },
        ...{ class: "p-3 rounded-xl bg-gray-900/80 border border-gray-800 hover:border-purple-500/50 hover:bg-gray-800/80 transition text-gray-300 flex flex-col gap-1" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
        ...{ class: "font-semibold text-purple-300" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
        ...{ class: "text-gray-500" },
    });
}
else {
    for (const [msg] of __VLS_getVForSourceType((__VLS_ctx.store.transcript))) {
        /** @type {[typeof MessageBubble, ]} */ ;
        // @ts-ignore
        const __VLS_12 = __VLS_asFunctionalComponent(MessageBubble, new MessageBubble({
            key: (msg.id),
            message: (msg),
        }));
        const __VLS_13 = __VLS_12({
            key: (msg.id),
            message: (msg),
        }, ...__VLS_functionalComponentArgsRest(__VLS_12));
    }
    if (__VLS_ctx.store.streamingText || __VLS_ctx.store.activeThinking) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "flex gap-3 my-4 animate-fadeIn" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "w-8 h-8 rounded-full bg-purple-900/60 border border-purple-500/30 flex items-center justify-center shrink-0" },
        });
        const __VLS_15 = {}.Loader2;
        /** @type {[typeof __VLS_components.Loader2, ]} */ ;
        // @ts-ignore
        const __VLS_16 = __VLS_asFunctionalComponent(__VLS_15, new __VLS_15({
            ...{ class: "w-4 h-4 text-purple-300 animate-spin" },
        }));
        const __VLS_17 = __VLS_16({
            ...{ class: "w-4 h-4 text-purple-300 animate-spin" },
        }, ...__VLS_functionalComponentArgsRest(__VLS_16));
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "max-w-[85%] rounded-2xl rounded-bl-sm px-4 py-3 bg-gray-900 border border-purple-500/40 text-gray-200 shadow-lg" },
        });
        if (__VLS_ctx.store.activeThinking) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
                ...{ class: "text-xs text-purple-300 font-mono mb-2 whitespace-pre-wrap opacity-90 border-b border-gray-800 pb-2" },
            });
            (__VLS_ctx.store.activeThinking);
        }
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "whitespace-pre-wrap leading-relaxed text-sm" },
        });
        (__VLS_ctx.store.streamingText);
    }
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "p-4 border-t border-gray-800 bg-gray-900/70 backdrop-blur shrink-0" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "max-w-4xl mx-auto" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "relative bg-gray-950 border border-gray-800 rounded-2xl focus-within:border-purple-500 focus-within:ring-1 focus-within:ring-purple-500 transition shadow-inner" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.textarea, __VLS_intrinsicElements.textarea)({
    ...{ onInput: (__VLS_ctx.adjustTextareaHeight) },
    ...{ onKeydown: (__VLS_ctx.handleKeydown) },
    ref: "textareaRef",
    value: (__VLS_ctx.inputPrompt),
    rows: "1",
    placeholder: "输入自然语言指令（如：构建项目、重构某函数... Enter 发送，Shift + Enter 换行）",
    disabled: (__VLS_ctx.isRunning),
    ...{ class: "w-full bg-transparent resize-none px-4 pt-3.5 pb-12 text-sm text-gray-200 placeholder-gray-500 focus:outline-none disabled:opacity-50 max-h-[180px]" },
});
/** @type {typeof __VLS_ctx.textareaRef} */ ;
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "absolute bottom-2.5 right-3 flex items-center gap-2" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "relative flex items-center" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex items-center gap-1.5 bg-gray-900/90 border border-gray-800 hover:border-gray-700 px-2.5 py-1.5 rounded-xl transition text-xs" },
});
const __VLS_19 = {}.Cpu;
/** @type {[typeof __VLS_components.Cpu, ]} */ ;
// @ts-ignore
const __VLS_20 = __VLS_asFunctionalComponent(__VLS_19, new __VLS_19({
    ...{ class: "w-3.5 h-3.5 text-purple-400 shrink-0" },
}));
const __VLS_21 = __VLS_20({
    ...{ class: "w-3.5 h-3.5 text-purple-400 shrink-0" },
}, ...__VLS_functionalComponentArgsRest(__VLS_20));
__VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
    ...{ onChange: (__VLS_ctx.handleModelChange) },
    value: (__VLS_ctx.selectedModelKey),
    disabled: (__VLS_ctx.isRunning),
    ...{ class: "bg-transparent text-xs text-gray-300 focus:outline-none cursor-pointer max-w-[180px] sm:max-w-[220px] truncate font-mono" },
});
if (__VLS_ctx.modelOptions.length === 0) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "",
        ...{ class: "bg-gray-900 text-gray-500" },
    });
}
for (const [opt] of __VLS_getVForSourceType((__VLS_ctx.modelOptions))) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        key: (opt.value),
        value: (opt.value),
        ...{ class: "bg-gray-900 text-gray-200" },
    });
    (opt.label);
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.handleSendOrAbort) },
    disabled: (!__VLS_ctx.isRunning && !__VLS_ctx.inputPrompt.trim()),
    ...{ class: "px-4 py-2 rounded-xl font-medium text-xs flex items-center gap-1.5 transition shadow-md disabled:opacity-40 disabled:cursor-not-allowed" },
    ...{ class: (__VLS_ctx.isRunning
            ? 'bg-rose-600 hover:bg-rose-500 active:bg-rose-700 text-white shadow-rose-950/50'
            : 'bg-purple-600 hover:bg-purple-500 active:bg-purple-700 text-white shadow-purple-950/50') },
});
if (__VLS_ctx.isRunning) {
    const __VLS_23 = {}.Square;
    /** @type {[typeof __VLS_components.Square, ]} */ ;
    // @ts-ignore
    const __VLS_24 = __VLS_asFunctionalComponent(__VLS_23, new __VLS_23({
        ...{ class: "w-3.5 h-3.5 fill-current" },
    }));
    const __VLS_25 = __VLS_24({
        ...{ class: "w-3.5 h-3.5 fill-current" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_24));
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
}
else {
    const __VLS_27 = {}.Send;
    /** @type {[typeof __VLS_components.Send, ]} */ ;
    // @ts-ignore
    const __VLS_28 = __VLS_asFunctionalComponent(__VLS_27, new __VLS_27({
        ...{ class: "w-3.5 h-3.5" },
    }));
    const __VLS_29 = __VLS_28({
        ...{ class: "w-3.5 h-3.5" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_28));
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex justify-between items-center px-2 pt-2 text-[11px] text-gray-500" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.kbd, __VLS_intrinsicElements.kbd)({
    ...{ class: "px-1.5 py-0.5 bg-gray-800 border border-gray-700 rounded text-gray-300" },
});
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-col']} */ ;
/** @type {__VLS_StyleScopedClasses['h-full']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-950']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-100']} */ ;
/** @type {__VLS_StyleScopedClasses['h-14']} */ ;
/** @type {__VLS_StyleScopedClasses['px-6']} */ ;
/** @type {__VLS_StyleScopedClasses['border-b']} */ ;
/** @type {__VLS_StyleScopedClasses['border-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-900/60']} */ ;
/** @type {__VLS_StyleScopedClasses['backdrop-blur']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-between']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-3']} */ ;
/** @type {__VLS_StyleScopedClasses['min-w-0']} */ ;
/** @type {__VLS_StyleScopedClasses['text-sm']} */ ;
/** @type {__VLS_StyleScopedClasses['font-semibold']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-200']} */ ;
/** @type {__VLS_StyleScopedClasses['truncate']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['px-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['py-0.5']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-full']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[11px]']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['w-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-full']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-4']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-400']} */ ;
/** @type {__VLS_StyleScopedClasses['hidden']} */ ;
/** @type {__VLS_StyleScopedClasses['sm:flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-400']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:text-purple-300']} */ ;
/** @type {__VLS_StyleScopedClasses['px-2']} */ ;
/** @type {__VLS_StyleScopedClasses['py-1']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['max-w-[200px]']} */ ;
/** @type {__VLS_StyleScopedClasses['truncate']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[11px]']} */ ;
/** @type {__VLS_StyleScopedClasses['px-6']} */ ;
/** @type {__VLS_StyleScopedClasses['py-2']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-rose-950/80']} */ ;
/** @type {__VLS_StyleScopedClasses['border-b']} */ ;
/** @type {__VLS_StyleScopedClasses['border-rose-800']} */ ;
/** @type {__VLS_StyleScopedClasses['text-rose-200']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-between']} */ ;
/** @type {__VLS_StyleScopedClasses['animate-fadeIn']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2']} */ ;
/** @type {__VLS_StyleScopedClasses['w-4']} */ ;
/** @type {__VLS_StyleScopedClasses['h-4']} */ ;
/** @type {__VLS_StyleScopedClasses['text-rose-400']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['text-rose-300']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:text-white']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['underline']} */ ;
/** @type {__VLS_StyleScopedClasses['ml-2']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-1']} */ ;
/** @type {__VLS_StyleScopedClasses['overflow-y-auto']} */ ;
/** @type {__VLS_StyleScopedClasses['px-6']} */ ;
/** @type {__VLS_StyleScopedClasses['py-6']} */ ;
/** @type {__VLS_StyleScopedClasses['space-y-4']} */ ;
/** @type {__VLS_StyleScopedClasses['h-full']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-col']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-center']} */ ;
/** @type {__VLS_StyleScopedClasses['text-center']} */ ;
/** @type {__VLS_StyleScopedClasses['max-w-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['mx-auto']} */ ;
/** @type {__VLS_StyleScopedClasses['py-12']} */ ;
/** @type {__VLS_StyleScopedClasses['space-y-6']} */ ;
/** @type {__VLS_StyleScopedClasses['w-16']} */ ;
/** @type {__VLS_StyleScopedClasses['h-16']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-2xl']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-purple-900/30']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-purple-500/30']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-center']} */ ;
/** @type {__VLS_StyleScopedClasses['shadow-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['shadow-purple-900/20']} */ ;
/** @type {__VLS_StyleScopedClasses['w-8']} */ ;
/** @type {__VLS_StyleScopedClasses['h-8']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['text-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['font-bold']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-200']} */ ;
/** @type {__VLS_StyleScopedClasses['text-sm']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-400']} */ ;
/** @type {__VLS_StyleScopedClasses['mt-1']} */ ;
/** @type {__VLS_StyleScopedClasses['grid']} */ ;
/** @type {__VLS_StyleScopedClasses['grid-cols-1']} */ ;
/** @type {__VLS_StyleScopedClasses['sm:grid-cols-2']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['w-full']} */ ;
/** @type {__VLS_StyleScopedClasses['text-left']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['p-3']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-900/80']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:border-purple-500/50']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-gray-800/80']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-300']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-col']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1']} */ ;
/** @type {__VLS_StyleScopedClasses['font-semibold']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-300']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-500']} */ ;
/** @type {__VLS_StyleScopedClasses['p-3']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-900/80']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:border-purple-500/50']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-gray-800/80']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-300']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-col']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1']} */ ;
/** @type {__VLS_StyleScopedClasses['font-semibold']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-300']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-500']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-3']} */ ;
/** @type {__VLS_StyleScopedClasses['my-4']} */ ;
/** @type {__VLS_StyleScopedClasses['animate-fadeIn']} */ ;
/** @type {__VLS_StyleScopedClasses['w-8']} */ ;
/** @type {__VLS_StyleScopedClasses['h-8']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-full']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-purple-900/60']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-purple-500/30']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-center']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['w-4']} */ ;
/** @type {__VLS_StyleScopedClasses['h-4']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-300']} */ ;
/** @type {__VLS_StyleScopedClasses['animate-spin']} */ ;
/** @type {__VLS_StyleScopedClasses['max-w-[85%]']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-2xl']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-bl-sm']} */ ;
/** @type {__VLS_StyleScopedClasses['px-4']} */ ;
/** @type {__VLS_StyleScopedClasses['py-3']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-900']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-purple-500/40']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-200']} */ ;
/** @type {__VLS_StyleScopedClasses['shadow-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-300']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['mb-2']} */ ;
/** @type {__VLS_StyleScopedClasses['whitespace-pre-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['opacity-90']} */ ;
/** @type {__VLS_StyleScopedClasses['border-b']} */ ;
/** @type {__VLS_StyleScopedClasses['border-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['pb-2']} */ ;
/** @type {__VLS_StyleScopedClasses['whitespace-pre-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['leading-relaxed']} */ ;
/** @type {__VLS_StyleScopedClasses['text-sm']} */ ;
/** @type {__VLS_StyleScopedClasses['p-4']} */ ;
/** @type {__VLS_StyleScopedClasses['border-t']} */ ;
/** @type {__VLS_StyleScopedClasses['border-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-900/70']} */ ;
/** @type {__VLS_StyleScopedClasses['backdrop-blur']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['max-w-4xl']} */ ;
/** @type {__VLS_StyleScopedClasses['mx-auto']} */ ;
/** @type {__VLS_StyleScopedClasses['relative']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-950']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-2xl']} */ ;
/** @type {__VLS_StyleScopedClasses['focus-within:border-purple-500']} */ ;
/** @type {__VLS_StyleScopedClasses['focus-within:ring-1']} */ ;
/** @type {__VLS_StyleScopedClasses['focus-within:ring-purple-500']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['shadow-inner']} */ ;
/** @type {__VLS_StyleScopedClasses['w-full']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-transparent']} */ ;
/** @type {__VLS_StyleScopedClasses['resize-none']} */ ;
/** @type {__VLS_StyleScopedClasses['px-4']} */ ;
/** @type {__VLS_StyleScopedClasses['pt-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['pb-12']} */ ;
/** @type {__VLS_StyleScopedClasses['text-sm']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-200']} */ ;
/** @type {__VLS_StyleScopedClasses['placeholder-gray-500']} */ ;
/** @type {__VLS_StyleScopedClasses['focus:outline-none']} */ ;
/** @type {__VLS_StyleScopedClasses['disabled:opacity-50']} */ ;
/** @type {__VLS_StyleScopedClasses['max-h-[180px]']} */ ;
/** @type {__VLS_StyleScopedClasses['absolute']} */ ;
/** @type {__VLS_StyleScopedClasses['bottom-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['right-3']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2']} */ ;
/** @type {__VLS_StyleScopedClasses['relative']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-900/90']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:border-gray-700']} */ ;
/** @type {__VLS_StyleScopedClasses['px-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['py-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-transparent']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-300']} */ ;
/** @type {__VLS_StyleScopedClasses['focus:outline-none']} */ ;
/** @type {__VLS_StyleScopedClasses['cursor-pointer']} */ ;
/** @type {__VLS_StyleScopedClasses['max-w-[180px]']} */ ;
/** @type {__VLS_StyleScopedClasses['sm:max-w-[220px]']} */ ;
/** @type {__VLS_StyleScopedClasses['truncate']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-900']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-500']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-900']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-200']} */ ;
/** @type {__VLS_StyleScopedClasses['px-4']} */ ;
/** @type {__VLS_StyleScopedClasses['py-2']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['shadow-md']} */ ;
/** @type {__VLS_StyleScopedClasses['disabled:opacity-40']} */ ;
/** @type {__VLS_StyleScopedClasses['disabled:cursor-not-allowed']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['fill-current']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-between']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['px-2']} */ ;
/** @type {__VLS_StyleScopedClasses['pt-2']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[11px]']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-500']} */ ;
/** @type {__VLS_StyleScopedClasses['px-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['py-0.5']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-gray-700']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-300']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            MessageBubble: MessageBubble,
            Send: Send,
            Square: Square,
            Sparkles: Sparkles,
            Loader2: Loader2,
            AlertTriangle: AlertTriangle,
            FolderGit2: FolderGit2,
            Cpu: Cpu,
            emit: emit,
            store: store,
            inputPrompt: inputPrompt,
            messagesContainer: messagesContainer,
            textareaRef: textareaRef,
            selectedModelKey: selectedModelKey,
            handleModelChange: handleModelChange,
            modelOptions: modelOptions,
            isRunning: isRunning,
            handleSendOrAbort: handleSendOrAbort,
            handleKeydown: handleKeydown,
            adjustTextareaHeight: adjustTextareaHeight,
            applyQuickPrompt: applyQuickPrompt,
        };
    },
    __typeEmits: {},
});
export default (await import('vue')).defineComponent({
    setup() {
        return {};
    },
    __typeEmits: {},
});
; /* PartiallyEnd: #4569/main.vue */
