/// <reference types="../../node_modules/.vue-global-types/vue_3.5_0_0_0.d.ts" />
import { ref } from 'vue';
import { ChevronDown, ChevronRight, Terminal, Wrench, CheckCircle2, AlertCircle } from 'lucide-vue-next';
const __VLS_props = defineProps();
const isOpen = ref(false);
debugger; /* PartiallyEnd: #3632/scriptSetup.vue */
const __VLS_ctx = {};
let __VLS_components;
let __VLS_directives;
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "my-2 rounded-lg border border-gray-800 bg-gray-900/80 overflow-hidden text-xs" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ onClick: (...[$event]) => {
            __VLS_ctx.isOpen = !__VLS_ctx.isOpen;
        } },
    ...{ class: "flex items-center justify-between px-3 py-2 bg-gray-800/60 cursor-pointer hover:bg-gray-800 transition" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex items-center gap-2" },
});
const __VLS_0 = ((__VLS_ctx.toolCall?.toolName === 'bash' ? __VLS_ctx.Terminal : __VLS_ctx.Wrench));
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent(__VLS_0, new __VLS_0({
    ...{ class: "w-4 h-4 text-purple-400" },
}));
const __VLS_2 = __VLS_1({
    ...{ class: "w-4 h-4 text-purple-400" },
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
    ...{ class: "font-mono font-semibold text-purple-300" },
});
(__VLS_ctx.toolCall?.toolName || __VLS_ctx.toolResult?.toolName);
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
    ...{ class: "text-gray-500 font-mono text-[11px] truncate max-w-xs" },
});
(__VLS_ctx.toolCall?.input ? JSON.stringify(__VLS_ctx.toolCall.input) : '');
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex items-center gap-2" },
});
if (__VLS_ctx.toolResult) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
        ...{ class: "flex items-center gap-1" },
    });
    if (!__VLS_ctx.toolResult.isError) {
        const __VLS_4 = {}.CheckCircle2;
        /** @type {[typeof __VLS_components.CheckCircle2, ]} */ ;
        // @ts-ignore
        const __VLS_5 = __VLS_asFunctionalComponent(__VLS_4, new __VLS_4({
            ...{ class: "w-3.5 h-3.5 text-emerald-400" },
        }));
        const __VLS_6 = __VLS_5({
            ...{ class: "w-3.5 h-3.5 text-emerald-400" },
        }, ...__VLS_functionalComponentArgsRest(__VLS_5));
    }
    else {
        const __VLS_8 = {}.AlertCircle;
        /** @type {[typeof __VLS_components.AlertCircle, ]} */ ;
        // @ts-ignore
        const __VLS_9 = __VLS_asFunctionalComponent(__VLS_8, new __VLS_8({
            ...{ class: "w-3.5 h-3.5 text-rose-400" },
        }));
        const __VLS_10 = __VLS_9({
            ...{ class: "w-3.5 h-3.5 text-rose-400" },
        }, ...__VLS_functionalComponentArgsRest(__VLS_9));
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
        ...{ class: (__VLS_ctx.toolResult.isError ? 'text-rose-400' : 'text-emerald-400') },
    });
    (__VLS_ctx.toolResult.isError ? '失败' : '完成');
}
else {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
        ...{ class: "text-amber-400 animate-pulse" },
    });
}
const __VLS_12 = ((__VLS_ctx.isOpen ? __VLS_ctx.ChevronDown : __VLS_ctx.ChevronRight));
// @ts-ignore
const __VLS_13 = __VLS_asFunctionalComponent(__VLS_12, new __VLS_12({
    ...{ class: "w-3.5 h-3.5 text-gray-400" },
}));
const __VLS_14 = __VLS_13({
    ...{ class: "w-3.5 h-3.5 text-gray-400" },
}, ...__VLS_functionalComponentArgsRest(__VLS_13));
if (__VLS_ctx.isOpen) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "p-3 border-t border-gray-800 space-y-2 bg-black/40" },
    });
    if (__VLS_ctx.toolCall?.input) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "text-gray-400 mb-1 font-semibold" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.pre, __VLS_intrinsicElements.pre)({
            ...{ class: "p-2 bg-gray-950 rounded text-gray-300 font-mono overflow-x-auto" },
        });
        (JSON.stringify(__VLS_ctx.toolCall.input, null, 2));
    }
    if (__VLS_ctx.toolResult) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "text-gray-400 mb-1 font-semibold" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.pre, __VLS_intrinsicElements.pre)({
            ...{ class: "p-2 bg-gray-950 rounded font-mono overflow-x-auto text-emerald-300" },
            ...{ class: ({ 'text-rose-300': __VLS_ctx.toolResult.isError }) },
        });
        (__VLS_ctx.toolResult.content.map(c => c.type === 'text' ? c.text : '').join('\n'));
    }
}
/** @type {__VLS_StyleScopedClasses['my-2']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-900/80']} */ ;
/** @type {__VLS_StyleScopedClasses['overflow-hidden']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-between']} */ ;
/** @type {__VLS_StyleScopedClasses['px-3']} */ ;
/** @type {__VLS_StyleScopedClasses['py-2']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-800/60']} */ ;
/** @type {__VLS_StyleScopedClasses['cursor-pointer']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2']} */ ;
/** @type {__VLS_StyleScopedClasses['w-4']} */ ;
/** @type {__VLS_StyleScopedClasses['h-4']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['font-semibold']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-300']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-500']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[11px]']} */ ;
/** @type {__VLS_StyleScopedClasses['truncate']} */ ;
/** @type {__VLS_StyleScopedClasses['max-w-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-emerald-400']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-rose-400']} */ ;
/** @type {__VLS_StyleScopedClasses['text-amber-400']} */ ;
/** @type {__VLS_StyleScopedClasses['animate-pulse']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-400']} */ ;
/** @type {__VLS_StyleScopedClasses['p-3']} */ ;
/** @type {__VLS_StyleScopedClasses['border-t']} */ ;
/** @type {__VLS_StyleScopedClasses['border-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['space-y-2']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-black/40']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-400']} */ ;
/** @type {__VLS_StyleScopedClasses['mb-1']} */ ;
/** @type {__VLS_StyleScopedClasses['font-semibold']} */ ;
/** @type {__VLS_StyleScopedClasses['p-2']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-950']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-300']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['overflow-x-auto']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-400']} */ ;
/** @type {__VLS_StyleScopedClasses['mb-1']} */ ;
/** @type {__VLS_StyleScopedClasses['font-semibold']} */ ;
/** @type {__VLS_StyleScopedClasses['p-2']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-950']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['overflow-x-auto']} */ ;
/** @type {__VLS_StyleScopedClasses['text-emerald-300']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            ChevronDown: ChevronDown,
            ChevronRight: ChevronRight,
            Terminal: Terminal,
            Wrench: Wrench,
            CheckCircle2: CheckCircle2,
            AlertCircle: AlertCircle,
            isOpen: isOpen,
        };
    },
    __typeProps: {},
});
export default (await import('vue')).defineComponent({
    setup() {
        return {};
    },
    __typeProps: {},
});
; /* PartiallyEnd: #4569/main.vue */
