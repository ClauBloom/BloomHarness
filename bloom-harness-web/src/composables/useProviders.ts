import { ref } from 'vue';
import type { ProviderConfig } from '@/types/session.types';

// 模块级缓存：模型选择器与设置页共享同一份 Provider 列表
const providers = ref<ProviderConfig[]>([]);
const loaded = ref(false);
const loading = ref(false);
const error = ref<string | null>(null);
let listening = false;

export const PROVIDERS_UPDATED_EVENT = 'bloom:providers-updated';

export function useProviders() {
  async function refresh(): Promise<ProviderConfig[]> {
    loading.value = true;
    error.value = null;
    try {
      const res = await fetch('/api/config/providers?reveal=false');
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const list = (await res.json()) as ProviderConfig[];
      providers.value = Array.isArray(list) ? list : [];
      loaded.value = true;
      return providers.value;
    } catch (e: any) {
      error.value = e?.message || String(e);
      return providers.value;
    } finally {
      loading.value = false;
    }
  }

  async function ensureLoaded(): Promise<ProviderConfig[]> {
    if (!listening) {
      listening = true;
      window.addEventListener(PROVIDERS_UPDATED_EVENT, () => void refresh());
    }
    if (loaded.value) return providers.value;
    return refresh();
  }

  function notifyUpdated() {
    window.dispatchEvent(new CustomEvent(PROVIDERS_UPDATED_EVENT));
  }

  return { providers, loaded, loading, error, refresh, ensureLoaded, notifyUpdated };
}
