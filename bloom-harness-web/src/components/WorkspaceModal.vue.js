/// <reference types="../../node_modules/.vue-global-types/vue_3.5_0_0_0.d.ts" />
import { ref, onMounted, watch } from 'vue';
import { Folder, FolderOpen, ChevronRight, ArrowUp, RotateCw, Check, AlertCircle, History, Home, Plus, X, Compass, FolderSearch, Loader2 } from 'lucide-vue-next';
const props = defineProps();
const emit = defineEmits();
const pathInput = ref(props.currentCwd || '');
const isValidating = ref(false);
const validationError = ref(null);
const validationSuccess = ref(false);
const isBrowsing = ref(false);
const currentBrowsePath = ref('');
const parentBrowsePath = ref('');
const directoryList = ref([]);
const defaultWorkspace = ref('');
const userHome = ref('');
const recentWorkspaces = ref([]);
const isPickingSystemFolder = ref(false);
onMounted(async () => {
    await loadCurrentInfo();
    await loadRecentWorkspaces();
    if (pathInput.value) {
        await validateAndBrowse(pathInput.value, false);
    }
    else if (defaultWorkspace.value) {
        pathInput.value = defaultWorkspace.value;
        await validateAndBrowse(defaultWorkspace.value, false);
    }
});
watch(() => props.currentCwd, (newVal) => {
    if (newVal) {
        pathInput.value = newVal;
        validateAndBrowse(newVal, false);
    }
});
async function loadCurrentInfo() {
    try {
        const res = await fetch('/api/workspace/current');
        if (res.ok) {
            const data = await res.json();
            defaultWorkspace.value = data.defaultWorkspace || '';
            userHome.value = data.userHome || '';
            if (!pathInput.value && defaultWorkspace.value) {
                pathInput.value = defaultWorkspace.value;
            }
        }
    }
    catch (e) {
        console.warn('Failed loading workspace current info:', e);
    }
}
async function loadRecentWorkspaces() {
    try {
        const res = await fetch('/api/workspace/recent');
        if (res.ok) {
            recentWorkspaces.value = await res.json();
        }
    }
    catch (e) {
        console.warn('Failed loading recent workspaces:', e);
    }
}
async function validatePath(pathStr) {
    if (!pathStr || !pathStr.trim()) {
        validationError.value = '请输入或选择有效的工作区路径';
        validationSuccess.value = false;
        return false;
    }
    isValidating.value = true;
    validationError.value = null;
    validationSuccess.value = false;
    try {
        const res = await fetch('/api/workspace/validate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ path: pathStr.trim() }),
        });
        if (res.ok) {
            const data = await res.json();
            if (data.valid) {
                pathInput.value = data.canonicalPath;
                validationSuccess.value = true;
                validationError.value = null;
                return true;
            }
            else {
                validationError.value = data.error || '无效的目录路径';
                validationSuccess.value = false;
                return false;
            }
        }
        else {
            validationError.value = `服务器校验失败 (HTTP ${res.status})`;
            return false;
        }
    }
    catch (e) {
        validationError.value = `请求异常: ${e.message || e}`;
        return false;
    }
    finally {
        isValidating.value = false;
    }
}
async function browseDirectory(targetPath) {
    isBrowsing.value = true;
    try {
        const url = targetPath ? `/api/workspace/browse?path=${encodeURIComponent(targetPath)}` : '/api/workspace/browse';
        const res = await fetch(url);
        if (res.ok) {
            const data = await res.json();
            currentBrowsePath.value = data.currentPath || '';
            parentBrowsePath.value = data.parentPath || '';
            directoryList.value = data.directories || [];
        }
    }
    catch (e) {
        console.warn('Failed browsing directory:', e);
    }
    finally {
        isBrowsing.value = false;
    }
}
async function validateAndBrowse(path, doBrowse = true) {
    pathInput.value = path;
    const ok = await validatePath(path);
    if (ok && doBrowse) {
        await browseDirectory(path);
    }
    else if (!currentBrowsePath.value) {
        await browseDirectory(path);
    }
}
function handleSelectDirectory(itemPath) {
    pathInput.value = itemPath;
    validateAndBrowse(itemPath, true);
}
function handleGoParent() {
    if (parentBrowsePath.value) {
        handleSelectDirectory(parentBrowsePath.value);
    }
}
async function handlePickSystemFolder() {
    isPickingSystemFolder.value = true;
    validationError.value = null;
    try {
        const res = await fetch('/api/workspace/pick-folder', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ initialPath: pathInput.value || defaultWorkspace.value }),
        });
        if (res.ok) {
            const data = await res.json();
            if (data.success && data.path) {
                await validateAndBrowse(data.path, true);
            }
            else if (data.message) {
                // Only set error if not cancelled
                if (!data.message.includes('已关闭')) {
                    validationError.value = data.message;
                }
            }
        }
    }
    catch (e) {
        validationError.value = `启动系统文件夹选择器失败: ${e.message || e}`;
    }
    finally {
        isPickingSystemFolder.value = false;
    }
}
async function handleConfirm(createNewSession) {
    const ok = await validatePath(pathInput.value);
    if (ok) {
        emit('selectWorkspace', pathInput.value.trim(), createNewSession);
        emit('close');
    }
}
debugger; /* PartiallyEnd: #3632/scriptSetup.vue */
const __VLS_ctx = {};
let __VLS_components;
let __VLS_directives;
// CSS variable injection 
// CSS variable injection end 
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4 animate-fadeIn" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "w-full max-w-2xl bg-gray-900 border border-gray-800 rounded-2xl shadow-2xl flex flex-col max-h-[90vh] overflow-hidden" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "h-14 px-6 border-b border-gray-800 flex items-center justify-between shrink-0 bg-gray-900/80" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex items-center gap-2.5" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "w-8 h-8 rounded-xl bg-purple-600/20 border border-purple-500/30 flex items-center justify-center text-purple-400" },
});
const __VLS_0 = {}.Compass;
/** @type {[typeof __VLS_components.Compass, ]} */ ;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent(__VLS_0, new __VLS_0({
    ...{ class: "w-4 h-4" },
}));
const __VLS_2 = __VLS_1({
    ...{ class: "w-4 h-4" },
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.h2, __VLS_intrinsicElements.h2)({
    ...{ class: "text-sm font-semibold text-gray-200" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({
    ...{ class: "text-[11px] text-gray-500" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (...[$event]) => {
            __VLS_ctx.emit('close');
        } },
    ...{ class: "p-1.5 rounded-lg text-gray-400 hover:text-gray-200 hover:bg-gray-800 transition" },
});
const __VLS_4 = {}.X;
/** @type {[typeof __VLS_components.X, ]} */ ;
// @ts-ignore
const __VLS_5 = __VLS_asFunctionalComponent(__VLS_4, new __VLS_4({
    ...{ class: "w-4 h-4" },
}));
const __VLS_6 = __VLS_5({
    ...{ class: "w-4 h-4" },
}, ...__VLS_functionalComponentArgsRest(__VLS_5));
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "p-6 overflow-y-auto space-y-5 flex-1 text-xs" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "space-y-1.5" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex items-center justify-between" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({
    ...{ class: "block font-medium text-gray-300" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.handlePickSystemFolder) },
    disabled: (__VLS_ctx.isPickingSystemFolder),
    ...{ class: "text-purple-400 hover:text-purple-300 flex items-center gap-1 text-[11px] font-medium transition disabled:opacity-50" },
    title: "调用操作系统原生文件管理器选择文件夹",
});
if (__VLS_ctx.isPickingSystemFolder) {
    const __VLS_8 = {}.Loader2;
    /** @type {[typeof __VLS_components.Loader2, ]} */ ;
    // @ts-ignore
    const __VLS_9 = __VLS_asFunctionalComponent(__VLS_8, new __VLS_8({
        ...{ class: "w-3.5 h-3.5 animate-spin" },
    }));
    const __VLS_10 = __VLS_9({
        ...{ class: "w-3.5 h-3.5 animate-spin" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_9));
}
else {
    const __VLS_12 = {}.FolderSearch;
    /** @type {[typeof __VLS_components.FolderSearch, ]} */ ;
    // @ts-ignore
    const __VLS_13 = __VLS_asFunctionalComponent(__VLS_12, new __VLS_12({
        ...{ class: "w-3.5 h-3.5" },
    }));
    const __VLS_14 = __VLS_13({
        ...{ class: "w-3.5 h-3.5" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_13));
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
(__VLS_ctx.isPickingSystemFolder ? '正在等待系统选择...' : '打开系统文件夹选择器');
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex items-center gap-2" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "relative flex-1" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
    ...{ onKeydown: (...[$event]) => {
            __VLS_ctx.validateAndBrowse(__VLS_ctx.pathInput, true);
        } },
    value: (__VLS_ctx.pathInput),
    type: "text",
    placeholder: "例如: /home/clau/codes/my-project",
    ...{ class: "w-full bg-gray-950 border border-gray-800 rounded-xl px-3 py-2.5 font-mono text-xs text-gray-200 placeholder-gray-600 focus:outline-none focus:border-purple-500 transition" },
});
if (__VLS_ctx.validationSuccess) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "absolute right-3 top-2.5 text-emerald-400 flex items-center gap-1" },
    });
    const __VLS_16 = {}.Check;
    /** @type {[typeof __VLS_components.Check, ]} */ ;
    // @ts-ignore
    const __VLS_17 = __VLS_asFunctionalComponent(__VLS_16, new __VLS_16({
        ...{ class: "w-3.5 h-3.5" },
    }));
    const __VLS_18 = __VLS_17({
        ...{ class: "w-3.5 h-3.5" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_17));
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
        ...{ class: "text-[10px]" },
    });
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.handlePickSystemFolder) },
    disabled: (__VLS_ctx.isPickingSystemFolder),
    ...{ class: "px-3.5 py-2.5 rounded-xl bg-purple-600/20 border border-purple-500/40 hover:bg-purple-600/30 text-purple-300 font-medium transition flex items-center gap-1.5 shrink-0 shadow-sm disabled:opacity-50" },
    title: "调用系统原生窗口选择本地文件夹",
});
if (__VLS_ctx.isPickingSystemFolder) {
    const __VLS_20 = {}.Loader2;
    /** @type {[typeof __VLS_components.Loader2, ]} */ ;
    // @ts-ignore
    const __VLS_21 = __VLS_asFunctionalComponent(__VLS_20, new __VLS_20({
        ...{ class: "w-3.5 h-3.5 animate-spin" },
    }));
    const __VLS_22 = __VLS_21({
        ...{ class: "w-3.5 h-3.5 animate-spin" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_21));
}
else {
    const __VLS_24 = {}.FolderSearch;
    /** @type {[typeof __VLS_components.FolderSearch, ]} */ ;
    // @ts-ignore
    const __VLS_25 = __VLS_asFunctionalComponent(__VLS_24, new __VLS_24({
        ...{ class: "w-3.5 h-3.5 text-purple-400" },
    }));
    const __VLS_26 = __VLS_25({
        ...{ class: "w-3.5 h-3.5 text-purple-400" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_25));
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (...[$event]) => {
            __VLS_ctx.validateAndBrowse(__VLS_ctx.pathInput, true);
        } },
    disabled: (__VLS_ctx.isValidating),
    ...{ class: "px-3.5 py-2.5 rounded-xl bg-gray-800 hover:bg-gray-700 text-gray-200 font-medium transition flex items-center gap-1.5 shrink-0" },
});
if (__VLS_ctx.isValidating) {
    const __VLS_28 = {}.RotateCw;
    /** @type {[typeof __VLS_components.RotateCw, ]} */ ;
    // @ts-ignore
    const __VLS_29 = __VLS_asFunctionalComponent(__VLS_28, new __VLS_28({
        ...{ class: "w-3.5 h-3.5 animate-spin" },
    }));
    const __VLS_30 = __VLS_29({
        ...{ class: "w-3.5 h-3.5 animate-spin" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_29));
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
if (__VLS_ctx.validationError) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "flex items-center gap-1 text-rose-400 text-[11px] pt-1" },
    });
    const __VLS_32 = {}.AlertCircle;
    /** @type {[typeof __VLS_components.AlertCircle, ]} */ ;
    // @ts-ignore
    const __VLS_33 = __VLS_asFunctionalComponent(__VLS_32, new __VLS_32({
        ...{ class: "w-3.5 h-3.5 shrink-0" },
    }));
    const __VLS_34 = __VLS_33({
        ...{ class: "w-3.5 h-3.5 shrink-0" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_33));
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
    (__VLS_ctx.validationError);
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "space-y-2" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "text-[11px] font-medium text-gray-400 flex items-center gap-1.5" },
});
const __VLS_36 = {}.History;
/** @type {[typeof __VLS_components.History, ]} */ ;
// @ts-ignore
const __VLS_37 = __VLS_asFunctionalComponent(__VLS_36, new __VLS_36({
    ...{ class: "w-3.5 h-3.5 text-purple-400" },
}));
const __VLS_38 = __VLS_37({
    ...{ class: "w-3.5 h-3.5 text-purple-400" },
}, ...__VLS_functionalComponentArgsRest(__VLS_37));
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex flex-wrap gap-2" },
});
if (__VLS_ctx.defaultWorkspace) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.defaultWorkspace))
                    return;
                __VLS_ctx.validateAndBrowse(__VLS_ctx.defaultWorkspace, true);
            } },
        ...{ class: "px-2.5 py-1 rounded-lg bg-gray-800/80 hover:bg-purple-900/30 border border-gray-700/60 hover:border-purple-500/50 text-gray-300 font-mono text-[11px] transition flex items-center gap-1.5" },
    });
    const __VLS_40 = {}.Home;
    /** @type {[typeof __VLS_components.Home, ]} */ ;
    // @ts-ignore
    const __VLS_41 = __VLS_asFunctionalComponent(__VLS_40, new __VLS_40({
        ...{ class: "w-3 h-3 text-purple-400" },
    }));
    const __VLS_42 = __VLS_41({
        ...{ class: "w-3 h-3 text-purple-400" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_41));
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
    (__VLS_ctx.defaultWorkspace.split('/').pop());
}
for (const [rec] of __VLS_getVForSourceType((__VLS_ctx.recentWorkspaces))) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                __VLS_ctx.validateAndBrowse(rec, true);
            } },
        key: (rec),
        ...{ class: "px-2.5 py-1 rounded-lg bg-gray-800/80 hover:bg-purple-900/30 border border-gray-700/60 hover:border-purple-500/50 text-gray-300 font-mono text-[11px] transition flex items-center gap-1.5 truncate max-w-xs" },
        title: (rec),
    });
    __VLS_asFunctionalDirective(__VLS_directives.vShow)(null, { ...__VLS_directiveBindingRestFields, value: (rec !== __VLS_ctx.defaultWorkspace) }, null, null);
    const __VLS_44 = {}.Folder;
    /** @type {[typeof __VLS_components.Folder, ]} */ ;
    // @ts-ignore
    const __VLS_45 = __VLS_asFunctionalComponent(__VLS_44, new __VLS_44({
        ...{ class: "w-3 h-3 text-gray-400" },
    }));
    const __VLS_46 = __VLS_45({
        ...{ class: "w-3 h-3 text-gray-400" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_45));
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
        ...{ class: "truncate" },
    });
    (rec);
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "space-y-2" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex items-center justify-between text-[11px] text-gray-400" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex items-center gap-1.5" },
});
const __VLS_48 = {}.FolderOpen;
/** @type {[typeof __VLS_components.FolderOpen, ]} */ ;
// @ts-ignore
const __VLS_49 = __VLS_asFunctionalComponent(__VLS_48, new __VLS_48({
    ...{ class: "w-3.5 h-3.5 text-purple-400" },
}));
const __VLS_50 = __VLS_49({
    ...{ class: "w-3.5 h-3.5 text-purple-400" },
}, ...__VLS_functionalComponentArgsRest(__VLS_49));
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
    ...{ class: "font-medium" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
    ...{ class: "font-mono text-[10px] text-gray-500 truncate max-w-xs" },
});
(__VLS_ctx.currentBrowsePath);
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "bg-gray-950 border border-gray-800 rounded-xl p-2 max-h-56 overflow-y-auto space-y-1" },
});
if (__VLS_ctx.parentBrowsePath) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.handleGoParent) },
        ...{ class: "w-full px-2.5 py-1.5 rounded-lg hover:bg-gray-900 text-left text-gray-400 hover:text-purple-300 flex items-center gap-2 transition" },
    });
    const __VLS_52 = {}.ArrowUp;
    /** @type {[typeof __VLS_components.ArrowUp, ]} */ ;
    // @ts-ignore
    const __VLS_53 = __VLS_asFunctionalComponent(__VLS_52, new __VLS_52({
        ...{ class: "w-3.5 h-3.5 text-purple-400" },
    }));
    const __VLS_54 = __VLS_53({
        ...{ class: "w-3.5 h-3.5 text-purple-400" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_53));
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
        ...{ class: "font-mono" },
    });
    (__VLS_ctx.parentBrowsePath);
}
if (__VLS_ctx.directoryList.length === 0) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "py-6 text-center text-gray-600 text-xs" },
    });
}
for (const [dir] of __VLS_getVForSourceType((__VLS_ctx.directoryList))) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ onClick: (...[$event]) => {
                __VLS_ctx.handleSelectDirectory(dir.path);
            } },
        key: (dir.path),
        ...{ class: "group px-2.5 py-1.5 rounded-lg hover:bg-gray-900 cursor-pointer flex items-center justify-between transition" },
        ...{ class: (__VLS_ctx.pathInput === dir.path ? 'bg-purple-950/40 border border-purple-800/40 text-purple-200' : 'text-gray-300') },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "flex items-center gap-2 truncate" },
    });
    const __VLS_56 = {}.Folder;
    /** @type {[typeof __VLS_components.Folder, ]} */ ;
    // @ts-ignore
    const __VLS_57 = __VLS_asFunctionalComponent(__VLS_56, new __VLS_56({
        ...{ class: "w-3.5 h-3.5 text-indigo-400 group-hover:text-purple-400 shrink-0" },
    }));
    const __VLS_58 = __VLS_57({
        ...{ class: "w-3.5 h-3.5 text-indigo-400 group-hover:text-purple-400 shrink-0" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_57));
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
        ...{ class: "truncate font-mono" },
    });
    (dir.name);
    const __VLS_60 = {}.ChevronRight;
    /** @type {[typeof __VLS_components.ChevronRight, ]} */ ;
    // @ts-ignore
    const __VLS_61 = __VLS_asFunctionalComponent(__VLS_60, new __VLS_60({
        ...{ class: "w-3 h-3 text-gray-600 group-hover:text-gray-400 shrink-0" },
    }));
    const __VLS_62 = __VLS_61({
        ...{ class: "w-3 h-3 text-gray-600 group-hover:text-gray-400 shrink-0" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_61));
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "p-4 px-6 border-t border-gray-800 bg-gray-900/90 flex items-center justify-between shrink-0" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "text-[11px] text-gray-500 font-mono truncate max-w-[280px]" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
    ...{ class: "text-purple-300" },
});
(__VLS_ctx.pathInput || '未选择');
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex items-center gap-2" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (...[$event]) => {
            __VLS_ctx.emit('close');
        } },
    ...{ class: "px-3 py-2 rounded-xl text-xs font-medium text-gray-400 hover:text-gray-200 hover:bg-gray-800 transition" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (...[$event]) => {
            __VLS_ctx.handleConfirm(true);
        } },
    title: "使用该工作区创建新会话",
    ...{ class: "px-3.5 py-2 rounded-xl text-xs font-medium bg-gray-800 hover:bg-gray-700 text-gray-200 transition flex items-center gap-1.5 shadow-sm" },
});
const __VLS_64 = {}.Plus;
/** @type {[typeof __VLS_components.Plus, ]} */ ;
// @ts-ignore
const __VLS_65 = __VLS_asFunctionalComponent(__VLS_64, new __VLS_64({
    ...{ class: "w-3.5 h-3.5 text-purple-400" },
}));
const __VLS_66 = __VLS_65({
    ...{ class: "w-3.5 h-3.5 text-purple-400" },
}, ...__VLS_functionalComponentArgsRest(__VLS_65));
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (...[$event]) => {
            __VLS_ctx.handleConfirm(false);
        } },
    title: "应用到当前会话工作区",
    ...{ class: "px-4 py-2 rounded-xl text-xs font-medium bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 text-white transition flex items-center gap-1.5 shadow-md shadow-purple-900/30" },
});
const __VLS_68 = {}.Check;
/** @type {[typeof __VLS_components.Check, ]} */ ;
// @ts-ignore
const __VLS_69 = __VLS_asFunctionalComponent(__VLS_68, new __VLS_68({
    ...{ class: "w-3.5 h-3.5" },
}));
const __VLS_70 = __VLS_69({
    ...{ class: "w-3.5 h-3.5" },
}, ...__VLS_functionalComponentArgsRest(__VLS_69));
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
/** @type {__VLS_StyleScopedClasses['fixed']} */ ;
/** @type {__VLS_StyleScopedClasses['inset-0']} */ ;
/** @type {__VLS_StyleScopedClasses['z-50']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-center']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-black/75']} */ ;
/** @type {__VLS_StyleScopedClasses['backdrop-blur-sm']} */ ;
/** @type {__VLS_StyleScopedClasses['p-4']} */ ;
/** @type {__VLS_StyleScopedClasses['animate-fadeIn']} */ ;
/** @type {__VLS_StyleScopedClasses['w-full']} */ ;
/** @type {__VLS_StyleScopedClasses['max-w-2xl']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-900']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-2xl']} */ ;
/** @type {__VLS_StyleScopedClasses['shadow-2xl']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-col']} */ ;
/** @type {__VLS_StyleScopedClasses['max-h-[90vh]']} */ ;
/** @type {__VLS_StyleScopedClasses['overflow-hidden']} */ ;
/** @type {__VLS_StyleScopedClasses['h-14']} */ ;
/** @type {__VLS_StyleScopedClasses['px-6']} */ ;
/** @type {__VLS_StyleScopedClasses['border-b']} */ ;
/** @type {__VLS_StyleScopedClasses['border-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-between']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-900/80']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['w-8']} */ ;
/** @type {__VLS_StyleScopedClasses['h-8']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-purple-600/20']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-purple-500/30']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-center']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['w-4']} */ ;
/** @type {__VLS_StyleScopedClasses['h-4']} */ ;
/** @type {__VLS_StyleScopedClasses['text-sm']} */ ;
/** @type {__VLS_StyleScopedClasses['font-semibold']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-200']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[11px]']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-500']} */ ;
/** @type {__VLS_StyleScopedClasses['p-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-400']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:text-gray-200']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['w-4']} */ ;
/** @type {__VLS_StyleScopedClasses['h-4']} */ ;
/** @type {__VLS_StyleScopedClasses['p-6']} */ ;
/** @type {__VLS_StyleScopedClasses['overflow-y-auto']} */ ;
/** @type {__VLS_StyleScopedClasses['space-y-5']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-1']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['space-y-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-between']} */ ;
/** @type {__VLS_StyleScopedClasses['block']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-300']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:text-purple-300']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[11px]']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['disabled:opacity-50']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['animate-spin']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2']} */ ;
/** @type {__VLS_StyleScopedClasses['relative']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-1']} */ ;
/** @type {__VLS_StyleScopedClasses['w-full']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-950']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['px-3']} */ ;
/** @type {__VLS_StyleScopedClasses['py-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-200']} */ ;
/** @type {__VLS_StyleScopedClasses['placeholder-gray-600']} */ ;
/** @type {__VLS_StyleScopedClasses['focus:outline-none']} */ ;
/** @type {__VLS_StyleScopedClasses['focus:border-purple-500']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['absolute']} */ ;
/** @type {__VLS_StyleScopedClasses['right-3']} */ ;
/** @type {__VLS_StyleScopedClasses['top-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-emerald-400']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[10px]']} */ ;
/** @type {__VLS_StyleScopedClasses['px-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['py-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-purple-600/20']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-purple-500/40']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-purple-600/30']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-300']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['shadow-sm']} */ ;
/** @type {__VLS_StyleScopedClasses['disabled:opacity-50']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['animate-spin']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['px-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['py-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-gray-700']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-200']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['animate-spin']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1']} */ ;
/** @type {__VLS_StyleScopedClasses['text-rose-400']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[11px]']} */ ;
/** @type {__VLS_StyleScopedClasses['pt-1']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['space-y-2']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[11px]']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-400']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2']} */ ;
/** @type {__VLS_StyleScopedClasses['px-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['py-1']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-800/80']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-purple-900/30']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-gray-700/60']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:border-purple-500/50']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-300']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[11px]']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['px-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['py-1']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-800/80']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-purple-900/30']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-gray-700/60']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:border-purple-500/50']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-300']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[11px]']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['truncate']} */ ;
/** @type {__VLS_StyleScopedClasses['max-w-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-400']} */ ;
/** @type {__VLS_StyleScopedClasses['truncate']} */ ;
/** @type {__VLS_StyleScopedClasses['space-y-2']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-between']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[11px]']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-400']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[10px]']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-500']} */ ;
/** @type {__VLS_StyleScopedClasses['truncate']} */ ;
/** @type {__VLS_StyleScopedClasses['max-w-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-950']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['p-2']} */ ;
/** @type {__VLS_StyleScopedClasses['max-h-56']} */ ;
/** @type {__VLS_StyleScopedClasses['overflow-y-auto']} */ ;
/** @type {__VLS_StyleScopedClasses['space-y-1']} */ ;
/** @type {__VLS_StyleScopedClasses['w-full']} */ ;
/** @type {__VLS_StyleScopedClasses['px-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['py-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-gray-900']} */ ;
/** @type {__VLS_StyleScopedClasses['text-left']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-400']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:text-purple-300']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['py-6']} */ ;
/** @type {__VLS_StyleScopedClasses['text-center']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-600']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['group']} */ ;
/** @type {__VLS_StyleScopedClasses['px-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['py-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-gray-900']} */ ;
/** @type {__VLS_StyleScopedClasses['cursor-pointer']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-between']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2']} */ ;
/** @type {__VLS_StyleScopedClasses['truncate']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-indigo-400']} */ ;
/** @type {__VLS_StyleScopedClasses['group-hover:text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['truncate']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-600']} */ ;
/** @type {__VLS_StyleScopedClasses['group-hover:text-gray-400']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['p-4']} */ ;
/** @type {__VLS_StyleScopedClasses['px-6']} */ ;
/** @type {__VLS_StyleScopedClasses['border-t']} */ ;
/** @type {__VLS_StyleScopedClasses['border-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-900/90']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-between']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[11px]']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-500']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['truncate']} */ ;
/** @type {__VLS_StyleScopedClasses['max-w-[280px]']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-300']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2']} */ ;
/** @type {__VLS_StyleScopedClasses['px-3']} */ ;
/** @type {__VLS_StyleScopedClasses['py-2']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-400']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:text-gray-200']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['px-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['py-2']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gray-800']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-gray-700']} */ ;
/** @type {__VLS_StyleScopedClasses['text-gray-200']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['shadow-sm']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['px-4']} */ ;
/** @type {__VLS_StyleScopedClasses['py-2']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-gradient-to-r']} */ ;
/** @type {__VLS_StyleScopedClasses['from-purple-600']} */ ;
/** @type {__VLS_StyleScopedClasses['to-indigo-600']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:from-purple-500']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:to-indigo-500']} */ ;
/** @type {__VLS_StyleScopedClasses['text-white']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['shadow-md']} */ ;
/** @type {__VLS_StyleScopedClasses['shadow-purple-900/30']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            Folder: Folder,
            FolderOpen: FolderOpen,
            ChevronRight: ChevronRight,
            ArrowUp: ArrowUp,
            RotateCw: RotateCw,
            Check: Check,
            AlertCircle: AlertCircle,
            History: History,
            Home: Home,
            Plus: Plus,
            X: X,
            Compass: Compass,
            FolderSearch: FolderSearch,
            Loader2: Loader2,
            emit: emit,
            pathInput: pathInput,
            isValidating: isValidating,
            validationError: validationError,
            validationSuccess: validationSuccess,
            currentBrowsePath: currentBrowsePath,
            parentBrowsePath: parentBrowsePath,
            directoryList: directoryList,
            defaultWorkspace: defaultWorkspace,
            recentWorkspaces: recentWorkspaces,
            isPickingSystemFolder: isPickingSystemFolder,
            validateAndBrowse: validateAndBrowse,
            handleSelectDirectory: handleSelectDirectory,
            handleGoParent: handleGoParent,
            handlePickSystemFolder: handlePickSystemFolder,
            handleConfirm: handleConfirm,
        };
    },
    __typeEmits: {},
    __typeProps: {},
});
export default (await import('vue')).defineComponent({
    setup() {
        return {};
    },
    __typeEmits: {},
    __typeProps: {},
});
; /* PartiallyEnd: #4569/main.vue */
