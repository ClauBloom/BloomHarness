/// <reference types="../../node_modules/.vue-global-types/vue_3.5_0_0_0.d.ts" />
import { computed } from 'vue';
import { renderMarkdown } from '@/utils/formatMarkdown';
const props = defineProps();
const renderedHtml = computed(() => renderMarkdown(props.content));
debugger; /* PartiallyEnd: #3632/scriptSetup.vue */
const __VLS_ctx = {};
let __VLS_components;
let __VLS_directives;
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "prose prose-invert max-w-none text-sm leading-relaxed" },
});
__VLS_asFunctionalDirective(__VLS_directives.vHtml)(null, { ...__VLS_directiveBindingRestFields, value: (__VLS_ctx.renderedHtml) }, null, null);
/** @type {__VLS_StyleScopedClasses['prose']} */ ;
/** @type {__VLS_StyleScopedClasses['prose-invert']} */ ;
/** @type {__VLS_StyleScopedClasses['max-w-none']} */ ;
/** @type {__VLS_StyleScopedClasses['text-sm']} */ ;
/** @type {__VLS_StyleScopedClasses['leading-relaxed']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            renderedHtml: renderedHtml,
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
