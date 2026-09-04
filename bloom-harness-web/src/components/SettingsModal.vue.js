/// <reference types="../../node_modules/.vue-global-types/vue_3.5_0_0_0.d.ts" />
import { ref, onMounted } from 'vue';
import { X, Settings, Key, Globe, Cpu, Save, Eye, EyeOff, Check, AlertCircle, Loader2, Plus, Trash2, Sparkles, Bot, Zap, CheckCircle2, XCircle } from 'lucide-vue-next';
const emit = defineEmits();
// Providers State
const providers = ref([]);
const selectedProviderId = ref('');
const isLoading = ref(false);
const isSaving = ref(false);
const isTesting = ref(false);
const isFetchingModels = ref(false);
const testResult = ref(null);
const statusMessage = ref(null);
const showApiKey = ref(false);
// New Model Input State
const newModelInput = ref('');
const currentProvider = ref({
    providerId: '',
    name: '',
    baseUrl: '',
    apiKey: '',
    protocol: 'openai',
    models: [],
});
onMounted(async () => {
    await loadProviders();
});
async function loadProviders() {
    isLoading.value = true;
    try {
        const res = await fetch('/api/config/providers?reveal=false');
        if (res.ok) {
            const data = await res.json();
            providers.value = data;
            if (providers.value.length > 0 && !selectedProviderId.value) {
                selectProvider(providers.value[0].providerId);
            }
        }
    }
    catch (e) {
        console.error('Failed to load providers:', e);
        showMessage('error', '加载供应商配置失败');
    }
    finally {
        isLoading.value = false;
    }
}
function selectProvider(id) {
    selectedProviderId.value = id;
    testResult.value = null;
    const p = providers.value.find(item => item.providerId === id);
    if (p) {
        currentProvider.value = {
            ...p,
            models: [...(p.models || [])]
        };
    }
    showApiKey.value = false;
}
function handleAddCustomProvider() {
    const newId = `custom-${Date.now().toString().slice(-4)}`;
    const newP = {
        providerId: newId,
        name: '新建自定义服务商',
        baseUrl: '',
        apiKey: '',
        protocol: 'openai',
        models: [],
        isConfigured: false,
    };
    providers.value.push(newP);
    selectProvider(newId);
}
function handleAddModel() {
    const m = newModelInput.value.trim();
    if (!m)
        return;
    if (!currentProvider.value.models) {
        currentProvider.value.models = [];
    }
    if (!currentProvider.value.models.includes(m)) {
        currentProvider.value.models.push(m);
    }
    newModelInput.value = '';
}
function handleRemoveModel(index) {
    currentProvider.value.models.splice(index, 1);
}
// Test Provider Connection
async function handleTestConnection() {
    if (!currentProvider.value.baseUrl) {
        showMessage('error', '请先填写 Base URL');
        return;
    }
    isTesting.value = true;
    testResult.value = null;
    try {
        const res = await fetch('/api/config/providers/test', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                providerId: currentProvider.value.providerId,
                baseUrl: currentProvider.value.baseUrl,
                apiKey: currentProvider.value.apiKey,
            }),
        });
        if (res.ok) {
            const result = await res.json();
            testResult.value = result;
        }
        else {
            testResult.value = { ok: false, message: `测试接口异常 HTTP ${res.status}` };
        }
    }
    catch (e) {
        testResult.value = { ok: false, message: `连接超时或网络异常: ${e.message || e}` };
    }
    finally {
        isTesting.value = false;
    }
}
// Fetch Model List from Upstream Provider
async function handleFetchModels() {
    if (!currentProvider.value.baseUrl) {
        showMessage('error', '请先填写 Base URL 才能获取模型列表');
        return;
    }
    isFetchingModels.value = true;
    try {
        const res = await fetch('/api/config/providers/fetch-models', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                providerId: currentProvider.value.providerId,
                baseUrl: currentProvider.value.baseUrl,
                apiKey: currentProvider.value.apiKey,
            }),
        });
        if (res.ok) {
            const data = await res.json();
            if (data.ok && Array.isArray(data.models) && data.models.length > 0) {
                // Merge without duplicates
                const existing = new Set(currentProvider.value.models || []);
                let addedCount = 0;
                for (const m of data.models) {
                    if (!existing.has(m)) {
                        currentProvider.value.models.push(m);
                        existing.add(m);
                        addedCount++;
                    }
                }
                showMessage('success', `成功获取并同步 ${data.models.length} 个模型 (新增 ${addedCount} 个)`);
            }
            else {
                showMessage('error', data.message || '未能拉取到模型列表');
            }
        }
        else {
            showMessage('error', `拉取模型接口异常 HTTP ${res.status}`);
        }
    }
    catch (e) {
        showMessage('error', `拉取模型失败: ${e.message || e}`);
    }
    finally {
        isFetchingModels.value = false;
    }
}
async function handleSaveProvider() {
    isSaving.value = true;
    statusMessage.value = null;
    try {
        const res = await fetch('/api/config/providers', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(currentProvider.value),
        });
        if (res.ok) {
            showMessage('success', `${currentProvider.value.name} 配置已保存并同步！`);
            const idx = providers.value.findIndex(p => p.providerId === currentProvider.value.providerId);
            const isKeyConfigured = !!currentProvider.value.apiKey && currentProvider.value.apiKey.length > 0;
            if (idx >= 0) {
                providers.value[idx] = {
                    ...currentProvider.value,
                    isConfigured: isKeyConfigured
                };
            }
            // Broadcast event so ChatPanel and other components immediately refresh their model lists
            window.dispatchEvent(new CustomEvent('bloom:providers-updated'));
        }
        else {
            const err = await res.json();
            showMessage('error', err.error || '保存失败');
        }
    }
    catch (e) {
        console.error('Failed saving provider:', e);
        showMessage('error', `保存异常: ${e.message || e}`);
    }
    finally {
        isSaving.value = false;
    }
}
async function handleDelete(providerId) {
    if (!confirm(`确定要移除 ${providerId} 供应商吗？`))
        return;
    try {
        const res = await fetch(`/api/config/providers/${providerId}`, {
            method: 'DELETE',
        });
        if (res.ok) {
            providers.value = providers.value.filter(p => p.providerId !== providerId);
            if (providers.value.length > 0) {
                selectProvider(providers.value[0].providerId);
            }
            showMessage('success', '已删除供应商配置');
        }
    }
    catch (e) {
        showMessage('error', '删除失败');
    }
}
function showMessage(type, text) {
    statusMessage.value = { type, text };
    setTimeout(() => {
        if (statusMessage.value?.text === text) {
            statusMessage.value = null;
        }
    }, 4000);
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
    ...{ class: "w-full max-w-3xl bg-zinc-900 border border-zinc-800 rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[85vh]" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "px-6 py-4 border-b border-zinc-800 flex items-center justify-between bg-zinc-950/80" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex items-center gap-3" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "w-8 h-8 rounded-xl bg-purple-950/80 border border-purple-500/30 flex items-center justify-center text-purple-400" },
});
const __VLS_0 = {}.Settings;
/** @type {[typeof __VLS_components.Settings, ]} */ ;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent(__VLS_0, new __VLS_0({
    ...{ class: "w-4 h-4" },
}));
const __VLS_2 = __VLS_1({
    ...{ class: "w-4 h-4" },
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.h2, __VLS_intrinsicElements.h2)({
    ...{ class: "text-sm font-semibold text-zinc-100 flex items-center gap-2" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
    ...{ class: "text-[10px] font-mono px-1.5 py-0.5 rounded bg-zinc-800 text-zinc-400 border border-zinc-700" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({
    ...{ class: "text-[11px] text-zinc-500 font-mono" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (...[$event]) => {
            __VLS_ctx.emit('close');
        } },
    ...{ class: "p-1.5 rounded-lg text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 transition" },
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
    ...{ class: "flex-1 flex overflow-hidden min-h-[440px]" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "w-56 border-r border-zinc-800 bg-zinc-950/40 p-3.5 flex flex-col justify-between shrink-0" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "space-y-1.5 overflow-y-auto" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex items-center justify-between text-[11px] font-semibold uppercase tracking-wider text-zinc-500 px-2 py-1" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
    ...{ class: "text-[10px] font-mono text-zinc-600" },
});
(__VLS_ctx.providers.length);
if (__VLS_ctx.providers.length === 0) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "p-3 text-center text-xs text-zinc-600" },
    });
}
for (const [p] of __VLS_getVForSourceType((__VLS_ctx.providers))) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                __VLS_ctx.selectProvider(p.providerId);
            } },
        key: (p.providerId),
        ...{ class: "w-full text-left px-3 py-2.5 rounded-xl text-xs font-medium flex items-center justify-between transition group border" },
        ...{ class: (__VLS_ctx.selectedProviderId === p.providerId
                ? 'bg-purple-950/40 text-purple-200 border-purple-800/50'
                : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900 border-transparent') },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "flex items-center gap-2 truncate" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
        ...{ class: "w-2 h-2 rounded-full shrink-0 transition-colors" },
        ...{ class: (p.isConfigured ? 'bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.5)]' : 'bg-zinc-600') },
        title: (p.isConfigured ? '凭证已配置' : '缺失 API Key'),
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
        ...{ class: "truncate" },
    });
    (p.name || p.providerId);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
        ...{ class: "text-[10px] font-mono text-zinc-600 group-hover:text-zinc-400" },
    });
    ((p.models || []).length);
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.handleAddCustomProvider) },
    ...{ class: "w-full py-2.5 px-3 rounded-xl border border-dashed border-zinc-700 hover:border-purple-500/80 hover:text-purple-300 text-zinc-400 text-xs flex items-center justify-center gap-1.5 transition bg-zinc-900/40 hover:bg-purple-950/20" },
});
const __VLS_8 = {}.Plus;
/** @type {[typeof __VLS_components.Plus, ]} */ ;
// @ts-ignore
const __VLS_9 = __VLS_asFunctionalComponent(__VLS_8, new __VLS_8({
    ...{ class: "w-3.5 h-3.5" },
}));
const __VLS_10 = __VLS_9({
    ...{ class: "w-3.5 h-3.5" },
}, ...__VLS_functionalComponentArgsRest(__VLS_9));
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex-1 p-6 overflow-y-auto space-y-4" },
});
if (__VLS_ctx.statusMessage) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "p-3 rounded-xl text-xs flex items-center gap-2 transition animate-fadeIn" },
        ...{ class: (__VLS_ctx.statusMessage.type === 'success'
                ? 'bg-emerald-950/40 border border-emerald-800/50 text-emerald-300'
                : 'bg-rose-950/40 border border-rose-800/50 text-rose-300') },
    });
    if (__VLS_ctx.statusMessage.type === 'success') {
        const __VLS_12 = {}.Check;
        /** @type {[typeof __VLS_components.Check, ]} */ ;
        // @ts-ignore
        const __VLS_13 = __VLS_asFunctionalComponent(__VLS_12, new __VLS_12({
            ...{ class: "w-4 h-4 shrink-0 text-emerald-400" },
        }));
        const __VLS_14 = __VLS_13({
            ...{ class: "w-4 h-4 shrink-0 text-emerald-400" },
        }, ...__VLS_functionalComponentArgsRest(__VLS_13));
    }
    else {
        const __VLS_16 = {}.AlertCircle;
        /** @type {[typeof __VLS_components.AlertCircle, ]} */ ;
        // @ts-ignore
        const __VLS_17 = __VLS_asFunctionalComponent(__VLS_16, new __VLS_16({
            ...{ class: "w-4 h-4 shrink-0 text-rose-400" },
        }));
        const __VLS_18 = __VLS_17({
            ...{ class: "w-4 h-4 shrink-0 text-rose-400" },
        }, ...__VLS_functionalComponentArgsRest(__VLS_17));
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
    (__VLS_ctx.statusMessage.text);
}
if (__VLS_ctx.currentProvider.providerId) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "space-y-4" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "grid grid-cols-3 gap-3.5" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "col-span-2" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({
        ...{ class: "block text-xs font-medium text-zinc-400 mb-1.5" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        value: (__VLS_ctx.currentProvider.name),
        type: "text",
        placeholder: "例如: DeepSeek / 本地 Ollama / 私有模型",
        ...{ class: "w-full bg-zinc-950 border border-zinc-800 focus:border-purple-500 rounded-xl px-3.5 py-2 text-xs text-zinc-200 focus:outline-none transition" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({
        ...{ class: "block text-xs font-medium text-zinc-400 mb-1.5" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
        value: (__VLS_ctx.currentProvider.protocol),
        ...{ class: "w-full bg-zinc-950 border border-zinc-800 focus:border-purple-500 rounded-xl px-3 py-2 text-xs text-zinc-200 focus:outline-none transition" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "openai",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "anthropic",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "deepseek",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "ollama",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({
        ...{ class: "block text-xs font-medium text-zinc-400 mb-1.5 flex items-center gap-1.5" },
    });
    const __VLS_20 = {}.Globe;
    /** @type {[typeof __VLS_components.Globe, ]} */ ;
    // @ts-ignore
    const __VLS_21 = __VLS_asFunctionalComponent(__VLS_20, new __VLS_20({
        ...{ class: "w-3.5 h-3.5 text-purple-400" },
    }));
    const __VLS_22 = __VLS_21({
        ...{ class: "w-3.5 h-3.5 text-purple-400" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_21));
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        value: (__VLS_ctx.currentProvider.baseUrl),
        type: "text",
        placeholder: "https://api.deepseek.com/v1 或本地 http://localhost:11434",
        ...{ class: "w-full bg-zinc-950 border border-zinc-800 focus:border-purple-500 rounded-xl px-3.5 py-2 text-xs font-mono text-zinc-200 focus:outline-none transition" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "bg-zinc-950/60 border border-zinc-800/80 rounded-xl p-3.5 space-y-3" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "flex items-center justify-between" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({
        ...{ class: "text-xs font-medium text-zinc-300 flex items-center gap-1.5" },
    });
    const __VLS_24 = {}.Key;
    /** @type {[typeof __VLS_components.Key, ]} */ ;
    // @ts-ignore
    const __VLS_25 = __VLS_asFunctionalComponent(__VLS_24, new __VLS_24({
        ...{ class: "w-3.5 h-3.5 text-purple-400" },
    }));
    const __VLS_26 = __VLS_25({
        ...{ class: "w-3.5 h-3.5 text-purple-400" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_25));
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.handleTestConnection) },
        type: "button",
        disabled: (__VLS_ctx.isTesting),
        ...{ class: "px-2.5 py-1 rounded-lg bg-zinc-800 hover:bg-zinc-700 text-zinc-300 text-[11px] font-medium flex items-center gap-1.5 transition disabled:opacity-50" },
    });
    if (__VLS_ctx.isTesting) {
        const __VLS_28 = {}.Loader2;
        /** @type {[typeof __VLS_components.Loader2, ]} */ ;
        // @ts-ignore
        const __VLS_29 = __VLS_asFunctionalComponent(__VLS_28, new __VLS_28({
            ...{ class: "w-3 h-3 animate-spin text-purple-400" },
        }));
        const __VLS_30 = __VLS_29({
            ...{ class: "w-3 h-3 animate-spin text-purple-400" },
        }, ...__VLS_functionalComponentArgsRest(__VLS_29));
    }
    else {
        const __VLS_32 = {}.Zap;
        /** @type {[typeof __VLS_components.Zap, ]} */ ;
        // @ts-ignore
        const __VLS_33 = __VLS_asFunctionalComponent(__VLS_32, new __VLS_32({
            ...{ class: "w-3 h-3 text-amber-400" },
        }));
        const __VLS_34 = __VLS_33({
            ...{ class: "w-3 h-3 text-amber-400" },
        }, ...__VLS_functionalComponentArgsRest(__VLS_33));
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
    (__VLS_ctx.isTesting ? '正在探测...' : '测试连通性');
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "relative" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        type: (__VLS_ctx.showApiKey ? 'text' : 'password'),
        placeholder: "sk-...",
        ...{ class: "w-full bg-zinc-900 border border-zinc-800 focus:border-purple-500 rounded-xl pl-3.5 pr-10 py-2 text-xs font-mono text-zinc-200 focus:outline-none transition" },
    });
    (__VLS_ctx.currentProvider.apiKey);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.currentProvider.providerId))
                    return;
                __VLS_ctx.showApiKey = !__VLS_ctx.showApiKey;
            } },
        type: "button",
        ...{ class: "absolute right-2.5 top-1/2 -translate-y-1/2 text-zinc-500 hover:text-zinc-300 p-1" },
    });
    if (__VLS_ctx.showApiKey) {
        const __VLS_36 = {}.EyeOff;
        /** @type {[typeof __VLS_components.EyeOff, ]} */ ;
        // @ts-ignore
        const __VLS_37 = __VLS_asFunctionalComponent(__VLS_36, new __VLS_36({
            ...{ class: "w-3.5 h-3.5" },
        }));
        const __VLS_38 = __VLS_37({
            ...{ class: "w-3.5 h-3.5" },
        }, ...__VLS_functionalComponentArgsRest(__VLS_37));
    }
    else {
        const __VLS_40 = {}.Eye;
        /** @type {[typeof __VLS_components.Eye, ]} */ ;
        // @ts-ignore
        const __VLS_41 = __VLS_asFunctionalComponent(__VLS_40, new __VLS_40({
            ...{ class: "w-3.5 h-3.5" },
        }));
        const __VLS_42 = __VLS_41({
            ...{ class: "w-3.5 h-3.5" },
        }, ...__VLS_functionalComponentArgsRest(__VLS_41));
    }
    if (__VLS_ctx.testResult) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "text-[11px] p-2 rounded-lg flex items-center gap-2" },
            ...{ class: (__VLS_ctx.testResult.ok ? 'bg-emerald-950/50 text-emerald-300 border border-emerald-800/40' : 'bg-rose-950/50 text-rose-300 border border-rose-800/40') },
        });
        if (__VLS_ctx.testResult.ok) {
            const __VLS_44 = {}.CheckCircle2;
            /** @type {[typeof __VLS_components.CheckCircle2, ]} */ ;
            // @ts-ignore
            const __VLS_45 = __VLS_asFunctionalComponent(__VLS_44, new __VLS_44({
                ...{ class: "w-3.5 h-3.5 shrink-0 text-emerald-400" },
            }));
            const __VLS_46 = __VLS_45({
                ...{ class: "w-3.5 h-3.5 shrink-0 text-emerald-400" },
            }, ...__VLS_functionalComponentArgsRest(__VLS_45));
        }
        else {
            const __VLS_48 = {}.XCircle;
            /** @type {[typeof __VLS_components.XCircle, ]} */ ;
            // @ts-ignore
            const __VLS_49 = __VLS_asFunctionalComponent(__VLS_48, new __VLS_48({
                ...{ class: "w-3.5 h-3.5 shrink-0 text-rose-400" },
            }));
            const __VLS_50 = __VLS_49({
                ...{ class: "w-3.5 h-3.5 shrink-0 text-rose-400" },
            }, ...__VLS_functionalComponentArgsRest(__VLS_49));
        }
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
        (__VLS_ctx.testResult.message);
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "space-y-2.5" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "flex items-center justify-between" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({
        ...{ class: "text-xs font-medium text-zinc-400 flex items-center gap-1.5" },
    });
    const __VLS_52 = {}.Cpu;
    /** @type {[typeof __VLS_components.Cpu, ]} */ ;
    // @ts-ignore
    const __VLS_53 = __VLS_asFunctionalComponent(__VLS_52, new __VLS_52({
        ...{ class: "w-3.5 h-3.5 text-purple-400" },
    }));
    const __VLS_54 = __VLS_53({
        ...{ class: "w-3.5 h-3.5 text-purple-400" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_53));
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "flex items-center gap-2" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.handleFetchModels) },
        type: "button",
        disabled: (__VLS_ctx.isFetchingModels || !__VLS_ctx.currentProvider.baseUrl),
        ...{ class: "px-2.5 py-1 rounded-lg bg-purple-950/50 hover:bg-purple-900/60 border border-purple-800/50 text-purple-300 text-[11px] font-medium flex items-center gap-1.5 transition disabled:opacity-50" },
    });
    if (__VLS_ctx.isFetchingModels) {
        const __VLS_56 = {}.Loader2;
        /** @type {[typeof __VLS_components.Loader2, ]} */ ;
        // @ts-ignore
        const __VLS_57 = __VLS_asFunctionalComponent(__VLS_56, new __VLS_56({
            ...{ class: "w-3 h-3 animate-spin text-purple-300" },
        }));
        const __VLS_58 = __VLS_57({
            ...{ class: "w-3 h-3 animate-spin text-purple-300" },
        }, ...__VLS_functionalComponentArgsRest(__VLS_57));
    }
    else {
        const __VLS_60 = {}.Sparkles;
        /** @type {[typeof __VLS_components.Sparkles, ]} */ ;
        // @ts-ignore
        const __VLS_61 = __VLS_asFunctionalComponent(__VLS_60, new __VLS_60({
            ...{ class: "w-3 h-3 text-purple-400" },
        }));
        const __VLS_62 = __VLS_61({
            ...{ class: "w-3 h-3 text-purple-400" },
        }, ...__VLS_functionalComponentArgsRest(__VLS_61));
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
    (__VLS_ctx.isFetchingModels ? '正在拉取...' : '一键获取模型列表');
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
        ...{ class: "text-[11px] text-zinc-500 font-mono" },
    });
    ((__VLS_ctx.currentProvider.models || []).length);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "flex items-center gap-2" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        ...{ onKeydown: (__VLS_ctx.handleAddModel) },
        value: (__VLS_ctx.newModelInput),
        type: "text",
        placeholder: "输入模型标识并回车 (如: deepseek-chat, qwen-2.5-coder)",
        ...{ class: "flex-1 bg-zinc-950 border border-zinc-800 focus:border-purple-500 rounded-xl px-3.5 py-2 text-xs font-mono text-zinc-200 focus:outline-none transition" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.handleAddModel) },
        type: "button",
        ...{ class: "px-3.5 py-2 rounded-xl bg-zinc-800 hover:bg-zinc-700 text-zinc-200 text-xs font-medium flex items-center gap-1 transition shrink-0" },
    });
    const __VLS_64 = {}.Plus;
    /** @type {[typeof __VLS_components.Plus, ]} */ ;
    // @ts-ignore
    const __VLS_65 = __VLS_asFunctionalComponent(__VLS_64, new __VLS_64({
        ...{ class: "w-3.5 h-3.5" },
    }));
    const __VLS_66 = __VLS_65({
        ...{ class: "w-3.5 h-3.5" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_65));
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "flex flex-wrap gap-2 pt-1 max-h-36 overflow-y-auto" },
    });
    for (const [model, idx] of __VLS_getVForSourceType((__VLS_ctx.currentProvider.models))) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            key: (model),
            ...{ class: "bg-zinc-950 border border-zinc-800 rounded-lg px-2.5 py-1.5 text-xs font-mono text-zinc-300 flex items-center gap-2 group hover:border-zinc-700" },
        });
        const __VLS_68 = {}.Sparkles;
        /** @type {[typeof __VLS_components.Sparkles, ]} */ ;
        // @ts-ignore
        const __VLS_69 = __VLS_asFunctionalComponent(__VLS_68, new __VLS_68({
            ...{ class: "w-3 h-3 text-purple-400" },
        }));
        const __VLS_70 = __VLS_69({
            ...{ class: "w-3 h-3 text-purple-400" },
        }, ...__VLS_functionalComponentArgsRest(__VLS_69));
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
        (model);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
            ...{ onClick: (...[$event]) => {
                    if (!(__VLS_ctx.currentProvider.providerId))
                        return;
                    __VLS_ctx.handleRemoveModel(idx);
                } },
            type: "button",
            ...{ class: "text-zinc-600 hover:text-rose-400 transition" },
            title: "移除模型",
        });
        const __VLS_72 = {}.X;
        /** @type {[typeof __VLS_components.X, ]} */ ;
        // @ts-ignore
        const __VLS_73 = __VLS_asFunctionalComponent(__VLS_72, new __VLS_72({
            ...{ class: "w-3 h-3" },
        }));
        const __VLS_74 = __VLS_73({
            ...{ class: "w-3 h-3" },
        }, ...__VLS_functionalComponentArgsRest(__VLS_73));
    }
    if (!__VLS_ctx.currentProvider.models || __VLS_ctx.currentProvider.models.length === 0) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "text-xs text-zinc-600 py-1" },
        });
    }
}
else {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "h-full flex flex-col items-center justify-center text-zinc-500 text-xs py-16" },
    });
    const __VLS_76 = {}.Bot;
    /** @type {[typeof __VLS_components.Bot, ]} */ ;
    // @ts-ignore
    const __VLS_77 = __VLS_asFunctionalComponent(__VLS_76, new __VLS_76({
        ...{ class: "w-8 h-8 text-zinc-700 mb-2" },
    }));
    const __VLS_78 = __VLS_77({
        ...{ class: "w-8 h-8 text-zinc-700 mb-2" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_77));
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "px-6 py-3.5 border-t border-zinc-800 bg-zinc-950 flex items-center justify-between" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({});
if (__VLS_ctx.currentProvider.providerId) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.currentProvider.providerId))
                    return;
                __VLS_ctx.handleDelete(__VLS_ctx.currentProvider.providerId);
            } },
        ...{ class: "text-xs text-rose-400 hover:text-rose-300 flex items-center gap-1.5 transition px-2 py-1 rounded hover:bg-rose-950/20" },
    });
    const __VLS_80 = {}.Trash2;
    /** @type {[typeof __VLS_components.Trash2, ]} */ ;
    // @ts-ignore
    const __VLS_81 = __VLS_asFunctionalComponent(__VLS_80, new __VLS_80({
        ...{ class: "w-3.5 h-3.5" },
    }));
    const __VLS_82 = __VLS_81({
        ...{ class: "w-3.5 h-3.5" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_81));
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "flex items-center gap-2.5" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (...[$event]) => {
            __VLS_ctx.emit('close');
        } },
    ...{ class: "px-4 py-2 rounded-xl text-xs text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 transition" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.handleSaveProvider) },
    disabled: (__VLS_ctx.isSaving || !__VLS_ctx.currentProvider.providerId),
    ...{ class: "px-4 py-2 rounded-xl bg-purple-600 hover:bg-purple-500 active:bg-purple-700 text-white font-medium text-xs flex items-center gap-1.5 transition shadow-md shadow-purple-950/40 disabled:opacity-50" },
});
if (__VLS_ctx.isSaving) {
    const __VLS_84 = {}.Loader2;
    /** @type {[typeof __VLS_components.Loader2, ]} */ ;
    // @ts-ignore
    const __VLS_85 = __VLS_asFunctionalComponent(__VLS_84, new __VLS_84({
        ...{ class: "w-3.5 h-3.5 animate-spin" },
    }));
    const __VLS_86 = __VLS_85({
        ...{ class: "w-3.5 h-3.5 animate-spin" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_85));
}
else {
    const __VLS_88 = {}.Save;
    /** @type {[typeof __VLS_components.Save, ]} */ ;
    // @ts-ignore
    const __VLS_89 = __VLS_asFunctionalComponent(__VLS_88, new __VLS_88({
        ...{ class: "w-3.5 h-3.5" },
    }));
    const __VLS_90 = __VLS_89({
        ...{ class: "w-3.5 h-3.5" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_89));
}
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
/** @type {__VLS_StyleScopedClasses['max-w-3xl']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-zinc-900']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-zinc-800']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-2xl']} */ ;
/** @type {__VLS_StyleScopedClasses['shadow-2xl']} */ ;
/** @type {__VLS_StyleScopedClasses['overflow-hidden']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-col']} */ ;
/** @type {__VLS_StyleScopedClasses['max-h-[85vh]']} */ ;
/** @type {__VLS_StyleScopedClasses['px-6']} */ ;
/** @type {__VLS_StyleScopedClasses['py-4']} */ ;
/** @type {__VLS_StyleScopedClasses['border-b']} */ ;
/** @type {__VLS_StyleScopedClasses['border-zinc-800']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-between']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-zinc-950/80']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-3']} */ ;
/** @type {__VLS_StyleScopedClasses['w-8']} */ ;
/** @type {__VLS_StyleScopedClasses['h-8']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-purple-950/80']} */ ;
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
/** @type {__VLS_StyleScopedClasses['text-zinc-100']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[10px]']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['px-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['py-0.5']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-zinc-800']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-400']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-zinc-700']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[11px]']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-500']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['p-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-400']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:text-zinc-200']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-zinc-800']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['w-4']} */ ;
/** @type {__VLS_StyleScopedClasses['h-4']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-1']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['overflow-hidden']} */ ;
/** @type {__VLS_StyleScopedClasses['min-h-[440px]']} */ ;
/** @type {__VLS_StyleScopedClasses['w-56']} */ ;
/** @type {__VLS_StyleScopedClasses['border-r']} */ ;
/** @type {__VLS_StyleScopedClasses['border-zinc-800']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-zinc-950/40']} */ ;
/** @type {__VLS_StyleScopedClasses['p-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-col']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-between']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['space-y-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['overflow-y-auto']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-between']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[11px]']} */ ;
/** @type {__VLS_StyleScopedClasses['font-semibold']} */ ;
/** @type {__VLS_StyleScopedClasses['uppercase']} */ ;
/** @type {__VLS_StyleScopedClasses['tracking-wider']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-500']} */ ;
/** @type {__VLS_StyleScopedClasses['px-2']} */ ;
/** @type {__VLS_StyleScopedClasses['py-1']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[10px]']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-600']} */ ;
/** @type {__VLS_StyleScopedClasses['p-3']} */ ;
/** @type {__VLS_StyleScopedClasses['text-center']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-600']} */ ;
/** @type {__VLS_StyleScopedClasses['w-full']} */ ;
/** @type {__VLS_StyleScopedClasses['text-left']} */ ;
/** @type {__VLS_StyleScopedClasses['px-3']} */ ;
/** @type {__VLS_StyleScopedClasses['py-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-between']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['group']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2']} */ ;
/** @type {__VLS_StyleScopedClasses['truncate']} */ ;
/** @type {__VLS_StyleScopedClasses['w-2']} */ ;
/** @type {__VLS_StyleScopedClasses['h-2']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-full']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['transition-colors']} */ ;
/** @type {__VLS_StyleScopedClasses['truncate']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[10px]']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-600']} */ ;
/** @type {__VLS_StyleScopedClasses['group-hover:text-zinc-400']} */ ;
/** @type {__VLS_StyleScopedClasses['w-full']} */ ;
/** @type {__VLS_StyleScopedClasses['py-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['px-3']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-dashed']} */ ;
/** @type {__VLS_StyleScopedClasses['border-zinc-700']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:border-purple-500/80']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:text-purple-300']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-400']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-zinc-900/40']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-purple-950/20']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-1']} */ ;
/** @type {__VLS_StyleScopedClasses['p-6']} */ ;
/** @type {__VLS_StyleScopedClasses['overflow-y-auto']} */ ;
/** @type {__VLS_StyleScopedClasses['space-y-4']} */ ;
/** @type {__VLS_StyleScopedClasses['p-3']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['animate-fadeIn']} */ ;
/** @type {__VLS_StyleScopedClasses['w-4']} */ ;
/** @type {__VLS_StyleScopedClasses['h-4']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['text-emerald-400']} */ ;
/** @type {__VLS_StyleScopedClasses['w-4']} */ ;
/** @type {__VLS_StyleScopedClasses['h-4']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['text-rose-400']} */ ;
/** @type {__VLS_StyleScopedClasses['space-y-4']} */ ;
/** @type {__VLS_StyleScopedClasses['grid']} */ ;
/** @type {__VLS_StyleScopedClasses['grid-cols-3']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['col-span-2']} */ ;
/** @type {__VLS_StyleScopedClasses['block']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-400']} */ ;
/** @type {__VLS_StyleScopedClasses['mb-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['w-full']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-zinc-950']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-zinc-800']} */ ;
/** @type {__VLS_StyleScopedClasses['focus:border-purple-500']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['px-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['py-2']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-200']} */ ;
/** @type {__VLS_StyleScopedClasses['focus:outline-none']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['block']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-400']} */ ;
/** @type {__VLS_StyleScopedClasses['mb-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['w-full']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-zinc-950']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-zinc-800']} */ ;
/** @type {__VLS_StyleScopedClasses['focus:border-purple-500']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['px-3']} */ ;
/** @type {__VLS_StyleScopedClasses['py-2']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-200']} */ ;
/** @type {__VLS_StyleScopedClasses['focus:outline-none']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['block']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-400']} */ ;
/** @type {__VLS_StyleScopedClasses['mb-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['w-full']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-zinc-950']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-zinc-800']} */ ;
/** @type {__VLS_StyleScopedClasses['focus:border-purple-500']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['px-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['py-2']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-200']} */ ;
/** @type {__VLS_StyleScopedClasses['focus:outline-none']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-zinc-950/60']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-zinc-800/80']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['p-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['space-y-3']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-between']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-300']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['px-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['py-1']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-zinc-800']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-zinc-700']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-300']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[11px]']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['disabled:opacity-50']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3']} */ ;
/** @type {__VLS_StyleScopedClasses['animate-spin']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3']} */ ;
/** @type {__VLS_StyleScopedClasses['text-amber-400']} */ ;
/** @type {__VLS_StyleScopedClasses['relative']} */ ;
/** @type {__VLS_StyleScopedClasses['w-full']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-zinc-900']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-zinc-800']} */ ;
/** @type {__VLS_StyleScopedClasses['focus:border-purple-500']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['pl-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['pr-10']} */ ;
/** @type {__VLS_StyleScopedClasses['py-2']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-200']} */ ;
/** @type {__VLS_StyleScopedClasses['focus:outline-none']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['absolute']} */ ;
/** @type {__VLS_StyleScopedClasses['right-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['top-1/2']} */ ;
/** @type {__VLS_StyleScopedClasses['-translate-y-1/2']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-500']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:text-zinc-300']} */ ;
/** @type {__VLS_StyleScopedClasses['p-1']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[11px]']} */ ;
/** @type {__VLS_StyleScopedClasses['p-2']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['text-emerald-400']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['text-rose-400']} */ ;
/** @type {__VLS_StyleScopedClasses['space-y-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-between']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-400']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2']} */ ;
/** @type {__VLS_StyleScopedClasses['px-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['py-1']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-purple-950/50']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-purple-900/60']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-purple-800/50']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-300']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[11px]']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['disabled:opacity-50']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3']} */ ;
/** @type {__VLS_StyleScopedClasses['animate-spin']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-300']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['text-[11px]']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-500']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-1']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-zinc-950']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-zinc-800']} */ ;
/** @type {__VLS_StyleScopedClasses['focus:border-purple-500']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['px-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['py-2']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-200']} */ ;
/** @type {__VLS_StyleScopedClasses['focus:outline-none']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['px-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['py-2']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-zinc-800']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-zinc-700']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-200']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['shrink-0']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2']} */ ;
/** @type {__VLS_StyleScopedClasses['pt-1']} */ ;
/** @type {__VLS_StyleScopedClasses['max-h-36']} */ ;
/** @type {__VLS_StyleScopedClasses['overflow-y-auto']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-zinc-950']} */ ;
/** @type {__VLS_StyleScopedClasses['border']} */ ;
/** @type {__VLS_StyleScopedClasses['border-zinc-800']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-lg']} */ ;
/** @type {__VLS_StyleScopedClasses['px-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['py-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['font-mono']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-300']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2']} */ ;
/** @type {__VLS_StyleScopedClasses['group']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:border-zinc-700']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3']} */ ;
/** @type {__VLS_StyleScopedClasses['text-purple-400']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-600']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:text-rose-400']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-600']} */ ;
/** @type {__VLS_StyleScopedClasses['py-1']} */ ;
/** @type {__VLS_StyleScopedClasses['h-full']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['flex-col']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-center']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-500']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['py-16']} */ ;
/** @type {__VLS_StyleScopedClasses['w-8']} */ ;
/** @type {__VLS_StyleScopedClasses['h-8']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-700']} */ ;
/** @type {__VLS_StyleScopedClasses['mb-2']} */ ;
/** @type {__VLS_StyleScopedClasses['px-6']} */ ;
/** @type {__VLS_StyleScopedClasses['py-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['border-t']} */ ;
/** @type {__VLS_StyleScopedClasses['border-zinc-800']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-zinc-950']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['justify-between']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['text-rose-400']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:text-rose-300']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['px-2']} */ ;
/** @type {__VLS_StyleScopedClasses['py-1']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-rose-950/20']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-2.5']} */ ;
/** @type {__VLS_StyleScopedClasses['px-4']} */ ;
/** @type {__VLS_StyleScopedClasses['py-2']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['text-zinc-400']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:text-zinc-200']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-zinc-800']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['px-4']} */ ;
/** @type {__VLS_StyleScopedClasses['py-2']} */ ;
/** @type {__VLS_StyleScopedClasses['rounded-xl']} */ ;
/** @type {__VLS_StyleScopedClasses['bg-purple-600']} */ ;
/** @type {__VLS_StyleScopedClasses['hover:bg-purple-500']} */ ;
/** @type {__VLS_StyleScopedClasses['active:bg-purple-700']} */ ;
/** @type {__VLS_StyleScopedClasses['text-white']} */ ;
/** @type {__VLS_StyleScopedClasses['font-medium']} */ ;
/** @type {__VLS_StyleScopedClasses['text-xs']} */ ;
/** @type {__VLS_StyleScopedClasses['flex']} */ ;
/** @type {__VLS_StyleScopedClasses['items-center']} */ ;
/** @type {__VLS_StyleScopedClasses['gap-1.5']} */ ;
/** @type {__VLS_StyleScopedClasses['transition']} */ ;
/** @type {__VLS_StyleScopedClasses['shadow-md']} */ ;
/** @type {__VLS_StyleScopedClasses['shadow-purple-950/40']} */ ;
/** @type {__VLS_StyleScopedClasses['disabled:opacity-50']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['animate-spin']} */ ;
/** @type {__VLS_StyleScopedClasses['w-3.5']} */ ;
/** @type {__VLS_StyleScopedClasses['h-3.5']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            X: X,
            Settings: Settings,
            Key: Key,
            Globe: Globe,
            Cpu: Cpu,
            Save: Save,
            Eye: Eye,
            EyeOff: EyeOff,
            Check: Check,
            AlertCircle: AlertCircle,
            Loader2: Loader2,
            Plus: Plus,
            Trash2: Trash2,
            Sparkles: Sparkles,
            Bot: Bot,
            Zap: Zap,
            CheckCircle2: CheckCircle2,
            XCircle: XCircle,
            emit: emit,
            providers: providers,
            selectedProviderId: selectedProviderId,
            isSaving: isSaving,
            isTesting: isTesting,
            isFetchingModels: isFetchingModels,
            testResult: testResult,
            statusMessage: statusMessage,
            showApiKey: showApiKey,
            newModelInput: newModelInput,
            currentProvider: currentProvider,
            selectProvider: selectProvider,
            handleAddCustomProvider: handleAddCustomProvider,
            handleAddModel: handleAddModel,
            handleRemoveModel: handleRemoveModel,
            handleTestConnection: handleTestConnection,
            handleFetchModels: handleFetchModels,
            handleSaveProvider: handleSaveProvider,
            handleDelete: handleDelete,
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
