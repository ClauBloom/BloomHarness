/// <reference types="../../node_modules/.vue-global-types/vue_3.5_0_0_0.d.ts" />
import { onMounted } from 'vue';
import { useAgentStore } from '@/stores/agentStore';
import { useSession } from '@/composables/useSession';
import { Plus, MessageSquare, Bot, Folder, Loader2, RotateCw, Settings } from 'lucide-vue-next';
const emit = defineEmits();
const store = useAgentStore();
const { fetchSessions, createSession, selectSession } = useSession();
onMounted(async () => {
    const list = await fetchSessions();
    if (list && list.length > 0) {
        if (!store.currentSessionId) {
            await selectSession(list[0].id);
        }
    }
    else {
        // 首次进入无会话时，自动创建一个初始会话，保障开箱即可使用
        console.log('No existing sessions found. Auto-creating initial session...');
        await createSession('默认工作会话');
    }
});
async function handleCreateNew() {
    if (store.isCreatingSession)
        return;
    await createSession();
}
async function handleRefresh() {
    await fetchSessions();
}
debugger; /* PartiallyEnd: #3632/scriptSetup.vue */
const __VLS_ctx = {};
let __VLS_components;
let __VLS_directives;
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "w-64 h-full bg-gray-900/90 border-r border-gray-800 flex flex-col shrink-0 select-none" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "h-14 px-4 border-b border-gray-800 flex items-center justify-between" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex items-center gap-2.5" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "w-7 h-7 rounded-lg bg-gradient-to-tr from-purple-600 to-indigo-500 flex items-center justify-center shadow-md shadow-purple-900/30" },
});
const __VLS_0 = {}.Bot;
/** @type {[typeof __VLS_components.Bot, ]} */ ;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent(__VLS_0, new __VLS_0({
    ...{ class: "w-4 h-4 text-white" },
}));
const __VLS_2 = __VLS_1({
    ...{ class: "w-4 h-4 text-white" },
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex flex-col" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
    ...{ class: "font-bold text-sm bg-gradient-to-r from-purple-300 via-indigo-200 to-purple-200 bg-clip-text text-transparent" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
    ...{ class: "text-[10px] text-gray-500 font-mono -mt-0.5" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex items-center gap-1" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (...[$event]) => {
            __VLS_ctx.emit('openWorkspace');
        } },
    title: "切换工作区",
    ...{ class: "p-1.5 rounded-lg text-gray-400 hover:text-purple-300 hover:bg-gray-800 transition" },
});
const __VLS_4 = {}.Folder;
/** @type {[typeof __VLS_components.Folder, ]} */ ;
// @ts-ignore
const __VLS_5 = __VLS_asFunctionalComponent(__VLS_4, new __VLS_4({
    ...{ class: "w-3.5 h-3.5" },
}));
const __VLS_6 = __VLS_5({
    ...{ class: "w-3.5 h-3.5" },
}, ...__VLS_functionalComponentArgsRest(__VLS_5));
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (...[$event]) => {
            __VLS_ctx.emit('openSettings');
        } },
    title: "模型与提供商设置",
    ...{ class: "p-1.5 rounded-lg text-gray-400 hover:text-gray-200 hover:bg-gray-800 transition" },
});
const __VLS_8 = {}.Settings;
/** @type {[typeof __VLS_components.Settings, ]} */ ;
// @ts-ignore
const __VLS_9 = __VLS_asFunctionalComponent(__VLS_8, new __VLS_8({
    ...{ class: "w-3.5 h-3.5" },
}));
const __VLS_10 = __VLS_9({
    ...{ class: "w-3.5 h-3.5" },
}, ...__VLS_functionalComponentArgsRest(__VLS_9));
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.handleRefresh) },
    title: "刷新会话列表",
    ...{ class: "p-1.5 rounded-lg text-gray-400 hover:text-gray-200 hover:bg-gray-800 transition" },
});
const __VLS_12 = {}.RotateCw;
/** @type {[typeof __VLS_components.RotateCw, ]} */ ;
// @ts-ignore
const __VLS_13 = __VLS_asFunctionalComponent(__VLS_12, new __VLS_12({
    ...{ class: "w-3.5 h-3.5" },
}));
const __VLS_14 = __VLS_13({
    ...{ class: "w-3.5 h-3.5" },
}, ...__VLS_functionalComponentArgsRest(__VLS_13));
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.handleCreateNew) },
    disabled: (__VLS_ctx.store.isCreatingSession),
    title: "新建会话",
    ...{ class: "p-1.5 rounded-lg bg-purple-600/80 hover:bg-purple-600 text-white transition disabled:opacity-50 flex items-center justify-center shadow-sm" },
});
if (__VLS_ctx.store.isCreatingSession) {
    const __VLS_16 = {}.Loader2;
    /** @type {[typeof __VLS_components.Loader2, ]} */ ;
    // @ts-ignore
    const __VLS_17 = __VLS_asFunctionalComponent(__VLS_16, new __VLS_16({
        ...{ class: "w-3.5 h-3.5 animate-spin" },
    }));
    const __VLS_18 = __VLS_17({
        ...{ class: "w-3.5 h-3.5 animate-spin" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_17));
}
else {
    const __VLS_20 = {}.Plus;
    /** @type {[typeof __VLS_components.Plus, ]} */ ;
    // @ts-ignore
    const __VLS_21 = __VLS_asFunctionalComponent(__VLS_20, new __VLS_20({
        ...{ class: "w-3.5 h-3.5" },
    }));
    const __VLS_22 = __VLS_21({
        ...{ class: "w-3.5 h-3.5" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_21));
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex-1 overflow-y-auto p-2 space-y-1" },
});
if (__VLS_ctx.store.sessionList.length === 0) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "h-40 flex flex-col items-center justify-center text-center p-4" },
    });
    if (__VLS_ctx.store.isCreatingSession) {
        const __VLS_24 = {}.Loader2;
        /** @type {[typeof __VLS_components.Loader2, ]} */ ;
        // @ts-ignore
        const __VLS_25 = __VLS_asFunctionalComponent(__VLS_24, new __VLS_24({
            ...{ class: "w-5 h-5 text-purple-400 animate-spin mb-2" },
        }));
        const __VLS_26 = __VLS_25({
            ...{ class: "w-5 h-5 text-purple-400 animate-spin mb-2" },
        }, ...__VLS_functionalComponentArgsRest(__VLS_25));
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({
        ...{ class: "text-xs text-gray-500" },
    });
    (__VLS_ctx.store.isCreatingSession ? '正在初始化会话...' : '暂无会话，请点击右上角 +');
}
for (const [s] of __VLS_getVForSourceType((__VLS_ctx.store.sessionList))) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ onClick: (...[$event]) => {
                __VLS_ctx.selectSession(s.id);
            } },
        key: (s.id),
        ...{ class: "group p-2.5 rounded-xl cursor-pointer flex items-center gap-2.5 text-xs transition border" },
        ...{ class: (__VLS_ctx.store.currentSessionId === s.id
                ? 'bg-purple-950/50 border-purple-800/50 text-purple-200 shadow-sm'
                : 'border-transparent hover:bg-gray-800/60 text-gray-400 hover:text-gray-200') },
    });
    const __VLS_28 = {}.MessageSquare;
    /** @type {[typeof __VLS_components.MessageSquare, ]} */ ;
    // @ts-ignore
    const __VLS_29 = __VLS_asFunctionalComponent(__VLS_28, new __VLS_28({
        ...{ class: "w-4 h-4 shrink-0" },
        ...{ class: (__VLS_ctx.store.currentSessionId === s.id ? 'text-purple-400' : 'text-gray-500') },
    }));
    const __VLS_30 = __VLS_29({
        ...{ class: "w-4 h-4 shrink-0" },
        ...{ class: (__VLS_ctx.store.currentSessionId === s.id ? 'text-purple-400' : 'text-gray-500') },
    }, ...__VLS_functionalComponentArgsRest(__VLS_29));
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "flex-1 truncate font-medium" },
    });
    (s.sessionName || s.id);
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "p-3 border-t border-gray-800 bg-gray-950/50 text-[11px] text-gray-400 flex flex-col gap-1" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ onClick: (...[$event]) => {
            __VLS_ctx.emit('openWorkspace');
        } },
    ...{ class: "flex items-center justify-between cursor-pointer group hover:text-purple-300 transition" },
    title: "点击选择/切换工作区",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex items-center gap-1.5 truncate" },
});
const __VLS_32 = {}.Folder;
/** @type {[typeof __VLS_components.Folder, ]} */ ;
// @ts-ignore
const __VLS_33 = __VLS_asFunctionalComponent(__VLS_32, new __VLS_32({
    ...{ class: "w-3.5 h-3.5 text-gray-500 group-hover:text-purple-400 shrink-0" },
}));
const __VLS_34 = __VLS_33({
    ...{ class: "w-3.5 h-3.5 text-gray-500 group-hover:text-purple-400 shrink-0" },
}, ...__VLS_functionalComponentArgsRest(__VLS_33));
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
    ...{ class: "truncate font-mono" },
    title: (__VLS_ctx.store.currentSession?.cwd || '默认工作区'),
});
(__VLS_ctx.store.currentSession?.cwd ? __VLS_ctx.store.currentSession.cwd.split('/').filter(Boolean).pop() : '工作区');
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
    ...{ class: "text-[10px] text-purple-400/80 group-hover:text-purple-300 font-sans shrink-0" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex items-center justify-between text-[10px] text-gray-500 pt-0.5" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
    ...{ class: "flex items-center gap-1 text-emerald-400" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
    ...{ class: "w-1.5 h-1.5 rounded-full bg-emerald-400" },
});
/** @type {__VLS_StyleScopedClasses['w-64']} */ ;
/** @type {__VLS_StyleScopedClasses['h-full']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-900/90']} */ ;
/** @type {__VLS_StyleScopedClasses['border-r']} */ ;
/** @type {__VLS_StyleScopedClasses['border-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-col']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['select-none']} */ ;
/** @type {__VLS_StyleScopedClasses['h-14']} */ ;
/** @type {__VLS_StyleScopedClasses['px-4']} */ ;
/** @type {__VLS_StyleScopedClasses['border-b']} */ ;
/** @type {__VLS_StyleScopedClasses['border-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-between']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['w-7']} */ ;
/** @type {__VLS_StyleScopedClasses['h-7']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gradient-to-tr']} */ ;
/** @type {__VLS_StyleScopedClasses['from-purple-600']} */ ;
/** @type {__VLS_StyleScopedClasses['to-indigo-500']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-center']} */ ;
/** @type {__VLS_StyleScopedClasses['shadow-md']} */ ;
/** @type {__VLS_StyleScopedClasses['shadow-purple-900/30']} */ ;
/** @type {__VLS_StyleScopedClasses['w-4']} */ ;
/** @type {__VLS_StyleScopedClasses['h-4']} */ ;
/** @type {__VLS_StyleScopedClasses['text-white']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-col']} */ ;
/** @type {__VLS_StyleScopedClasses['font-bold']} */ ;
/** @type {__VLS_StyleScopedClasses['text-sm']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gradient-to-r']} */ ;
/** @type {__VLS_StyleScopedClasses['from-purple-300']} */ ;
/** @type {__VLS_StyleScopedClasses['via-indigo-200']} */ ;
/** @type {__VLS_StyleScopedClasses['to-purple-200']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-clip-text']} */ ;
/** @type {__VLS_StyleScopedClasses['text-transparent']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[10px]']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-500']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['-mt-0.5']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1']} */ ;
/** @type {__VLS_StyleScopedClasses['p-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-400']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:text-purple-300']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['p-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-400']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:text-gray-200']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['p-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-400']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:text-gray-200']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['p-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-purple-600/80']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-purple-600']} */ ;
/** @type {__VLS_StyleScopedClasses['text-white']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['disabled:opacity-50']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-center']} */ ;
/** @type {__VLS_StyleScopedClasses['shadow-sm']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['animate-spin']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-1']} */ ;
/** @type {__VLS_StyleScopedClasses['overflow-y-auto']} */ ;
/** @type {__VLS_StyleScopedClasses['p-2']} */ ;
/** @type {__VLS_StyleScopedClasses['space-y-1']} */ ;
/** @type {__VLS_StyleScopedClasses['h-40']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-col']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-center']} */ ;
/** @type {__VLS_StyleScopedClasses['text-center']} */ ;
/** @type {__VLS_StyleScopedClasses['p-4']} */ ;
/** @type {__VLS_StyleScopedClasses['w-5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['animate-spin']} */ ;
/** @type {__VLS_StyleScopedClasses['mb-2']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-500']} */ ;
/** @type {__VLS_StyleScopedClasses['group']} */ ;
/** @type {__VLS_StyleScopedClasses['p-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['cursor-pointer']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['w-4']} */ ;
/** @type {__VLS_StyleScopedClasses['h-4']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-1']} */ ;
/** @type {__VLS_StyleScopedClasses['truncate']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['p-3']} */ ;
/** @type {__VLS_StyleScopedClasses['border-t']} */ ;
/** @type {__VLS_StyleScopedClasses['border-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-950/50']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[11px]']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-400']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-col']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-between']} */ ;
/** @type {__VLS_StyleScopedClasses['cursor-pointer']} */ ;
/** @type {__VLS_StyleScopedClasses['group']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:text-purple-300']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['truncate']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-500']} */ ;
/** @type {__VLS_StyleScopedClasses['group-hover:text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['truncate']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[10px]']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400/80']} */ ;
/** @type {__VLS_StyleScopedClasses['group-hover:text-purple-300']} */ ;
/** @type {__VLS_StyleScopedClasses['font-sans']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-between']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[10px]']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-500']} */ ;
/** @type {__VLS_StyleScopedClasses['pt-0.5']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1']} */ ;
/** @type {__VLS_StyleScopedClasses['text-emerald-400']} */ ;
/** @type {__VLS_StyleScopedClasses['w-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-full']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-emerald-400']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            Plus: Plus,
            MessageSquare: MessageSquare,
            Bot: Bot,
            Folder: Folder,
            Loader2: Loader2,
            RotateCw: RotateCw,
            Settings: Settings,
            emit: emit,
            store: store,
            selectSession: selectSession,
            handleCreateNew: handleCreateNew,
            handleRefresh: handleRefresh,
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
