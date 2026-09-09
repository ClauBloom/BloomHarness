import { reactive, readonly } from 'vue';

/**
 * Theme runtime (port of dsh ui-theme): appearance preference (light / dark /
 * system) and the conversation font-size axis. The DOM contract is the one the
 * dsh token sheets expect:
 *   - `body[data-ds-dark-theme]` present ⇒ dark palette
 *   - `html.style.colorScheme` for native UA chrome
 *   - `--dsh-content-font-size` on <body> (integer px, 12–17)
 *   - `<meta name="theme-color">` mirrors the resolved body background
 * Persistence is localStorage (`bloom.ui-theme`); index.html carries an inline
 * boot script reading the same key so the first paint has no flash.
 */
export type ThemePreference = 'light' | 'dark' | 'system';

export const THEME_STORAGE_KEY = 'bloom.ui-theme';
export const THEME_PREFERENCES: readonly ThemePreference[] = ['light', 'dark', 'system'];
export const DEFAULT_PREFERENCE: ThemePreference = 'system';
export const FONT_SIZE_MIN = 12;
export const FONT_SIZE_MAX = 17;
export const DEFAULT_FONT_SIZE = 14;

interface PersistedTheme {
  preference?: ThemePreference;
  fontSize?: number;
}

interface ThemeState {
  preference: ThemePreference;
  fontSize: number;
  /** Resolved palette (system preference already applied). */
  dark: boolean;
}

function readPersisted(): PersistedTheme {
  try {
    const raw = localStorage.getItem(THEME_STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as PersistedTheme;
    return typeof parsed === 'object' && parsed ? parsed : {};
  } catch {
    return {};
  }
}

function normalizePreference(value: unknown): ThemePreference {
  return THEME_PREFERENCES.includes(value as ThemePreference) ? (value as ThemePreference) : DEFAULT_PREFERENCE;
}

function normalizeFontSize(value: unknown): number {
  const n = typeof value === 'number' && Number.isFinite(value) ? Math.round(value) : DEFAULT_FONT_SIZE;
  return Math.min(FONT_SIZE_MAX, Math.max(FONT_SIZE_MIN, n));
}

const media: MediaQueryList | null =
  typeof matchMedia === 'function' ? matchMedia('(prefers-color-scheme: dark)') : null;

function resolveDark(preference: ThemePreference): boolean {
  if (preference === 'dark') return true;
  if (preference === 'light') return false;
  return media?.matches ?? false;
}

const persisted = readPersisted();
const state = reactive<ThemeState>({
  preference: normalizePreference(persisted.preference),
  fontSize: normalizeFontSize(persisted.fontSize),
  dark: resolveDark(normalizePreference(persisted.preference)),
});

function persist() {
  try {
    localStorage.setItem(
      THEME_STORAGE_KEY,
      JSON.stringify({ preference: state.preference, fontSize: state.fontSize }),
    );
  } catch {
    // storage unavailable: theme stays session-local
  }
}

function applyToDocument() {
  const dark = resolveDark(state.preference);
  state.dark = dark;
  document.documentElement.style.colorScheme = dark ? 'dark' : 'light';
  document.body.toggleAttribute('data-ds-dark-theme', dark);
  document.body.style.setProperty('--dsh-content-font-size', `${state.fontSize}px`);
  let meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
  if (!meta) {
    meta = document.createElement('meta');
    meta.name = 'theme-color';
    document.head.appendChild(meta);
  }
  meta.content = getComputedStyle(document.body).backgroundColor;
}

let initialized = false;

/** Apply the persisted theme and start following the OS preference. Idempotent. */
export function initTheme() {
  if (initialized) return;
  initialized = true;
  applyToDocument();
  media?.addEventListener('change', () => {
    if (state.preference === 'system') applyToDocument();
  });
}

export function useTheme() {
  function setPreference(preference: ThemePreference) {
    state.preference = normalizePreference(preference);
    persist();
    applyToDocument();
  }

  function setFontSize(fontSize: number) {
    state.fontSize = normalizeFontSize(fontSize);
    persist();
    applyToDocument();
  }

  return {
    theme: readonly(state),
    setPreference,
    setFontSize,
  };
}
