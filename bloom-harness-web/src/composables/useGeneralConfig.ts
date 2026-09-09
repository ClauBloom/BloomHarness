import { ref } from 'vue';
import type { GeneralSettings } from '@/types/session.types';

export const DEFAULT_SYSTEM_PROMPT = 'You are an expert AI software engineer pair programming in BloomHarness.';

const settings = ref<GeneralSettings>({
  defaultProvider: '',
  defaultModel: '',
  temperature: 0.7,
  maxTokens: 4096,
  systemPrompt: DEFAULT_SYSTEM_PROMPT,
});

const loaded = ref(false);
const loading = ref(false);
const saving = ref(false);
const error = ref<string | null>(null);

export function useGeneralConfig() {
  async function fetchGeneralSettings(): Promise<GeneralSettings> {
    loading.value = true;
    error.value = null;
    try {
      const res = await fetch('/api/config/general');
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      if (data && typeof data === 'object') {
        settings.value = {
          defaultProvider: typeof data.defaultProvider === 'string' ? data.defaultProvider : '',
          defaultModel: typeof data.defaultModel === 'string' ? data.defaultModel : '',
          temperature: typeof data.temperature === 'number' ? data.temperature : 0.7,
          maxTokens: typeof data.maxTokens === 'number' ? data.maxTokens : 4096,
          systemPrompt: typeof data.systemPrompt === 'string' ? data.systemPrompt : DEFAULT_SYSTEM_PROMPT,
        };
        loaded.value = true;
      }
    } catch (e: any) {
      error.value = e?.message || String(e);
      console.warn('Failed to load general settings:', e);
    } finally {
      loading.value = false;
    }
    return settings.value;
  }

  async function ensureLoaded(): Promise<GeneralSettings> {
    if (loaded.value) return settings.value;
    return fetchGeneralSettings();
  }

  async function saveGeneralSettings(partial: Partial<GeneralSettings>): Promise<boolean> {
    saving.value = true;
    error.value = null;
    try {
      const payload = {
        ...settings.value,
        ...partial,
      };
      const res = await fetch('/api/config/general', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const resData = await res.json();
      if (resData && resData.settings) {
        settings.value = {
          defaultProvider: typeof resData.settings.defaultProvider === 'string' ? resData.settings.defaultProvider : payload.defaultProvider,
          defaultModel: typeof resData.settings.defaultModel === 'string' ? resData.settings.defaultModel : payload.defaultModel,
          temperature: typeof resData.settings.temperature === 'number' ? resData.settings.temperature : payload.temperature,
          maxTokens: typeof resData.settings.maxTokens === 'number' ? resData.settings.maxTokens : payload.maxTokens,
          systemPrompt: typeof resData.settings.systemPrompt === 'string' ? resData.settings.systemPrompt : payload.systemPrompt,
        };
      } else {
        settings.value = payload as GeneralSettings;
      }
      return true;
    } catch (e: any) {
      error.value = e?.message || String(e);
      console.error('Failed to save general settings:', e);
      return false;
    } finally {
      saving.value = false;
    }
  }

  return {
    settings,
    loaded,
    loading,
    saving,
    error,
    fetchGeneralSettings,
    ensureLoaded,
    saveGeneralSettings,
  };
}
