/// <reference types="../node_modules/.vue-global-types/vue_3.5_0_0_0.d.ts" />
import { ref } from 'vue';
import SessionSelector from '@/components/SessionSelector.vue';
import ChatPanel from '@/components/ChatPanel.vue';
import SettingsModal from '@/components/SettingsModal.vue';
import WorkspaceModal from '@/components/WorkspaceModal.vue';
import { useAgentStore } from '@/stores/agentStore';
import { useSession } from '@/composables/useSession';
const store = useAgentStore();
const { createSession, updateSessionCwd, selectSession } = useSession();
const showSettings = ref(false);
const showWorkspaceModal = ref(false);
async function handleSelectWorkspace(path, createNewSession) {
    if (createNewSession || !store.currentSessionId) {
        const dirName = path.split('/').filter(Boolean).pop() || '工作区';
        await createSession(`${dirName} 会话`, path);
    }
    else {
        await updateSessionCwd(store.currentSessionId, path);
        if (store.currentSessionId) {
            await selectSession(store.currentSessionId);
        }
    }
}
debugger; /* PartiallyEnd: #3632/scriptSetup.vue */
const __VLS_ctx = {};
let __VLS_components;
let __VLS_directives;
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex h-screen w-screen overflow-hidden bg-gray-950 font-sans" },
});
/** @type {[typeof SessionSelector, ]} */ ;
// @ts-ignore
const __VLS_0 = __VLS_asFunctionalComponent(SessionSelector, new SessionSelector({
    ...{ 'onOpenSettings': {} },
    ...{ 'onOpenWorkspace': {} },
}));
const __VLS_1 = __VLS_0({
    ...{ 'onOpenSettings': {} },
    ...{ 'onOpenWorkspace': {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_0));
let __VLS_3;
let __VLS_4;
let __VLS_5;
const __VLS_6 = {
    onOpenSettings: (...[$event]) => {
        __VLS_ctx.showSettings = true;
    }
};
const __VLS_7 = {
    onOpenWorkspace: (...[$event]) => {
        __VLS_ctx.showWorkspaceModal = true;
    }
};
var __VLS_2;
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex-1 flex flex-col min-w-0" },
});
/** @type {[typeof ChatPanel, ]} */ ;
// @ts-ignore
const __VLS_8 = __VLS_asFunctionalComponent(ChatPanel, new ChatPanel({
    ...{ 'onOpenSettings': {} },
    ...{ 'onOpenWorkspace': {} },
}));
const __VLS_9 = __VLS_8({
    ...{ 'onOpenSettings': {} },
    ...{ 'onOpenWorkspace': {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_8));
let __VLS_11;
let __VLS_12;
let __VLS_13;
const __VLS_14 = {
    onOpenSettings: (...[$event]) => {
        __VLS_ctx.showSettings = true;
    }
};
const __VLS_15 = {
    onOpenWorkspace: (...[$event]) => {
        __VLS_ctx.showWorkspaceModal = true;
    }
};
var __VLS_10;
if (__VLS_ctx.showSettings) {
    /** @type {[typeof SettingsModal, ]} */ ;
    // @ts-ignore
    const __VLS_16 = __VLS_asFunctionalComponent(SettingsModal, new SettingsModal({
        ...{ 'onClose': {} },
    }));
    const __VLS_17 = __VLS_16({
        ...{ 'onClose': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_16));
    let __VLS_19;
    let __VLS_20;
    let __VLS_21;
    const __VLS_22 = {
        onClose: (...[$event]) => {
            if (!(__VLS_ctx.showSettings))
                return;
            __VLS_ctx.showSettings = false;
        }
    };
    var __VLS_18;
}
if (__VLS_ctx.showWorkspaceModal) {
    /** @type {[typeof WorkspaceModal, ]} */ ;
    // @ts-ignore
    const __VLS_23 = __VLS_asFunctionalComponent(WorkspaceModal, new WorkspaceModal({
        ...{ 'onClose': {} },
        ...{ 'onSelectWorkspace': {} },
        currentCwd: (__VLS_ctx.store.currentSession?.cwd),
    }));
    const __VLS_24 = __VLS_23({
        ...{ 'onClose': {} },
        ...{ 'onSelectWorkspace': {} },
        currentCwd: (__VLS_ctx.store.currentSession?.cwd),
    }, ...__VLS_functionalComponentArgsRest(__VLS_23));
    let __VLS_26;
    let __VLS_27;
    let __VLS_28;
    const __VLS_29 = {
        onClose: (...[$event]) => {
            if (!(__VLS_ctx.showWorkspaceModal))
                return;
            __VLS_ctx.showWorkspaceModal = false;
        }
    };
    const __VLS_30 = {
        onSelectWorkspace: (__VLS_ctx.handleSelectWorkspace)
    };
    var __VLS_25;
}
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['h-screen']} */ ;
/** @type {__VLS_StyleScopedClasses['w-screen']} */ ;
/** @type {__VLS_StyleScopedClasses['overflow-hidden']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-950']} */ ;
/** @type {__VLS_StyleScopedClasses['font-sans']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-1']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-col']} */ ;
/** @type {__VLS_StyleScopedClasses['min-w-0']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            SessionSelector: SessionSelector,
            ChatPanel: ChatPanel,
            SettingsModal: SettingsModal,
            WorkspaceModal: WorkspaceModal,
            store: store,
            showSettings: showSettings,
            showWorkspaceModal: showWorkspaceModal,
            handleSelectWorkspace: handleSelectWorkspace,
        };
    },
});
export default (await import('vue')).defineComponent({
    setup() {
        return {};
    },
});
; /* PartiallyEnd: #4569/main.vue */
