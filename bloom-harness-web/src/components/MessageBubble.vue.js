/// <reference types="../../node_modules/.vue-global-types/vue_3.5_0_0_0.d.ts" />
import MarkdownRenderer from './MarkdownRenderer.vue';
import ToolExecution from './ToolExecution.vue';
import { User, Bot, Brain } from 'lucide-vue-next';
const __VLS_props = defineProps();
// 兜底提取历史消息文本中内嵌的 <think>...</think> 思考内容
function parseTextContent(raw) {
    if (!raw)
        return { thinking: null, text: '' };
    const thinkMatch = raw.match(/<think>([\s\S]*?)(?:<\/think>|$)/i);
    if (thinkMatch) {
        const thinking = thinkMatch[1].trim();
        const text = raw.replace(/<think>[\s\S]*?(?:<\/think>|$)/i, '').trim();
        return { thinking, text };
    }
    return { thinking: null, text: raw };
}
debugger; /* PartiallyEnd: #3632/scriptSetup.vue */
const __VLS_ctx = {};
let __VLS_components;
let __VLS_directives;
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex gap-3 my-4 group" },
    ...{ class: ({ 'justify-end': __VLS_ctx.message.role === 'user' }) },
});
if (__VLS_ctx.message.role !== 'user') {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "w-8 h-8 rounded-full bg-purple-900/60 border border-purple-500/30 flex items-center justify-center shrink-0" },
    });
    const __VLS_0 = {}.Bot;
    /** @type {[typeof __VLS_components.Bot, ]} */ ;
    // @ts-ignore
    const __VLS_1 = __VLS_asFunctionalComponent(__VLS_0, new __VLS_0({
        ...{ class: "w-4 h-4 text-purple-300" },
    }));
    const __VLS_2 = __VLS_1({
        ...{ class: "w-4 h-4 text-purple-300" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_1));
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "max-w-[85%] rounded-2xl px-4 py-3 shadow-md transition" },
    ...{ class: ([
            __VLS_ctx.message.role === 'user'
                ? 'bg-purple-600 text-white rounded-br-sm'
                : __VLS_ctx.message.status === 'error'
                    ? 'bg-rose-950/40 border border-rose-800/60 text-rose-200 rounded-bl-sm shadow-rose-950/20'
                    : 'bg-gray-900 border border-gray-800 text-gray-200 rounded-bl-sm'
        ]) },
});
if (__VLS_ctx.message.status === 'error') {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "flex items-center gap-1.5 text-xs font-semibold text-rose-400 mb-2 pb-1.5 border-b border-rose-800/40" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
        ...{ class: "w-2 h-2 rounded-full bg-rose-500 animate-pulse" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
}
for (const [item, idx] of __VLS_getVForSourceType((__VLS_ctx.message.content))) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        key: (idx),
    });
    if (item.type === 'text') {
        if (__VLS_ctx.parseTextContent(item.text).thinking) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
                ...{ class: "my-2 p-2.5 rounded-lg bg-gray-950/70 border border-purple-900/40 text-xs text-purple-300 font-mono" },
            });
            __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
                ...{ class: "flex items-center gap-1.5 font-semibold text-purple-400 mb-1" },
            });
            const __VLS_4 = {}.Brain;
            /** @type {[typeof __VLS_components.Brain, ]} */ ;
            // @ts-ignore
            const __VLS_5 = __VLS_asFunctionalComponent(__VLS_4, new __VLS_4({
                ...{ class: "w-3.5 h-3.5 animate-pulse" },
            }));
            const __VLS_6 = __VLS_5({
                ...{ class: "w-3.5 h-3.5 animate-pulse" },
            }, ...__VLS_functionalComponentArgsRest(__VLS_5));
            __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
            __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
                ...{ class: "whitespace-pre-wrap leading-relaxed opacity-90" },
            });
            (__VLS_ctx.parseTextContent(item.text).thinking);
        }
        if (__VLS_ctx.parseTextContent(item.text).text) {
            /** @type {[typeof MarkdownRenderer, ]} */ ;
            // @ts-ignore
            const __VLS_8 = __VLS_asFunctionalComponent(MarkdownRenderer, new MarkdownRenderer({
                content: (__VLS_ctx.parseTextContent(item.text).text),
            }));
            const __VLS_9 = __VLS_8({
                content: (__VLS_ctx.parseTextContent(item.text).text),
            }, ...__VLS_functionalComponentArgsRest(__VLS_8));
        }
    }
    else if (item.type === 'thinking') {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "my-2 p-2.5 rounded-lg bg-gray-950/70 border border-purple-900/40 text-xs text-purple-300 font-mono" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "flex items-center gap-1.5 font-semibold text-purple-400 mb-1" },
        });
        const __VLS_11 = {}.Brain;
        /** @type {[typeof __VLS_components.Brain, ]} */ ;
        // @ts-ignore
        const __VLS_12 = __VLS_asFunctionalComponent(__VLS_11, new __VLS_11({
            ...{ class: "w-3.5 h-3.5 animate-pulse" },
        }));
        const __VLS_13 = __VLS_12({
            ...{ class: "w-3.5 h-3.5 animate-pulse" },
        }, ...__VLS_functionalComponentArgsRest(__VLS_12));
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "whitespace-pre-wrap leading-relaxed opacity-90" },
        });
        (item.thinking);
    }
    else if (item.type === 'toolCall') {
        /** @type {[typeof ToolExecution, ]} */ ;
        // @ts-ignore
        const __VLS_15 = __VLS_asFunctionalComponent(ToolExecution, new ToolExecution({
            toolCall: item,
        }));
        const __VLS_16 = __VLS_15({
            toolCall: item,
        }, ...__VLS_functionalComponentArgsRest(__VLS_15));
    }
}
if (__VLS_ctx.message.role === 'tool') {
    /** @type {[typeof ToolExecution, ]} */ ;
    // @ts-ignore
    const __VLS_18 = __VLS_asFunctionalComponent(ToolExecution, new ToolExecution({
        toolResult: __VLS_ctx.message,
    }));
    const __VLS_19 = __VLS_18({
        toolResult: __VLS_ctx.message,
    }, ...__VLS_functionalComponentArgsRest(__VLS_18));
}
if (__VLS_ctx.message.role === 'user') {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "w-8 h-8 rounded-full bg-gray-800 border border-gray-700 flex items-center justify-center shrink-0" },
    });
    const __VLS_21 = {}.User;
    /** @type {[typeof __VLS_components.User, ]} */ ;
    // @ts-ignore
    const __VLS_22 = __VLS_asFunctionalComponent(__VLS_21, new __VLS_21({
        ...{ class: "w-4 h-4 text-gray-300" },
    }));
    const __VLS_23 = __VLS_22({
        ...{ class: "w-4 h-4 text-gray-300" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_22));
}
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-3']} */ ;
/** @type {__VLS_StyleScopedClasses['my-4']} */ ;
/** @type {__VLS_StyleScopedClasses['group']} */ ;
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
/** @type {__VLS_StyleScopedClasses['max-w-[85%]']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-2xl']} */ ;
/** @type {__VLS_StyleScopedClasses['px-4']} */ ;
/** @type {__VLS_StyleScopedClasses['py-3']} */ ;
/** @type {__VLS_StyleScopedClasses['shadow-md']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['font-semibold']} */ ;
/** @type {__VLS_StyleScopedClasses['text-rose-400']} */ ;
/** @type {__VLS_StyleScopedClasses['mb-2']} */ ;
/** @type {__VLS_StyleScopedClasses['pb-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['border-b']} */ ;
/** @type {__VLS_StyleScopedClasses['border-rose-800/40']} */ ;
/** @type {__VLS_StyleScopedClasses['w-2']} */ ;
/** @type {__VLS_StyleScopedClasses['h-2']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-full']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-rose-500']} */ ;
/** @type {__VLS_StyleScopedClasses['animate-pulse']} */ ;
/** @type {__VLS_StyleScopedClasses['my-2']} */ ;
/** @type {__VLS_StyleScopedClasses['p-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-950/70']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-purple-900/40']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-300']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['font-semibold']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['mb-1']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['animate-pulse']} */ ;
/** @type {__VLS_StyleScopedClasses['whitespace-pre-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['leading-relaxed']} */ ;
/** @type {__VLS_StyleScopedClasses['opacity-90']} */ ;
/** @type {__VLS_StyleScopedClasses['my-2']} */ ;
/** @type {__VLS_StyleScopedClasses['p-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-950/70']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-purple-900/40']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-300']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['font-semibold']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['mb-1']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['animate-pulse']} */ ;
/** @type {__VLS_StyleScopedClasses['whitespace-pre-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['leading-relaxed']} */ ;
/** @type {__VLS_StyleScopedClasses['opacity-90']} */ ;
/** @type {__VLS_StyleScopedClasses['w-8']} */ ;
/** @type {__VLS_StyleScopedClasses['h-8']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-full']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-gray-700']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-center']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['w-4']} */ ;
/** @type {__VLS_StyleScopedClasses['h-4']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-300']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            MarkdownRenderer: MarkdownRenderer,
            ToolExecution: ToolExecution,
            User: User,
            Bot: Bot,
            Brain: Brain,
            parseTextContent: parseTextContent,
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
