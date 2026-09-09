/**
 * The app's ONE syntax highlighter (port of dsh ui-primitives/markdown/highlight.ts):
 * a synchronous fine-grained shiki core with the JavaScript regex engine (no
 * WASM), an explicit grammar allowlist and a CSS-variables theme. Token colors
 * come from `--shiki-*` custom properties in styles/shiki.css (light + dark).
 *
 * TypeScript, shell and JSON load at boot; the wider set is imported lazily the
 * first time a language is requested. Unknown languages render plain.
 */
import { createHighlighterCoreSync, createCssVariablesTheme } from 'shiki/core';
import type { HighlighterCore } from 'shiki/core';
import { createJavaScriptRegexEngine } from 'shiki/engine/javascript';
import langTs from '@shikijs/langs/typescript';
import langBash from '@shikijs/langs/shellscript';
import langJson from '@shikijs/langs/json';

type LangModule = { default: typeof langTs };

const THEME_NAME = 'css-variables';

const LAZY_GRAMMARS = new Map<string, () => Promise<LangModule>>([
  ['python', () => import('@shikijs/langs/python')],
  ['ruby', () => import('@shikijs/langs/ruby')],
  ['go', () => import('@shikijs/langs/go')],
  ['rust', () => import('@shikijs/langs/rust')],
  ['java', () => import('@shikijs/langs/java')],
  ['c', () => import('@shikijs/langs/c')],
  ['cpp', () => import('@shikijs/langs/cpp')],
  ['csharp', () => import('@shikijs/langs/csharp')],
  ['kotlin', () => import('@shikijs/langs/kotlin')],
  ['swift', () => import('@shikijs/langs/swift')],
  ['php', () => import('@shikijs/langs/php')],
  ['yaml', () => import('@shikijs/langs/yaml')],
  ['toml', () => import('@shikijs/langs/toml')],
  ['ini', () => import('@shikijs/langs/ini')],
  ['markdown', () => import('@shikijs/langs/markdown')],
  ['html', () => import('@shikijs/langs/html')],
  ['css', () => import('@shikijs/langs/css')],
  ['scss', () => import('@shikijs/langs/scss')],
  ['sql', () => import('@shikijs/langs/sql')],
  ['xml', () => import('@shikijs/langs/xml')],
  ['lua', () => import('@shikijs/langs/lua')],
]);

const LANG_ALIASES = new Map<string, string>([
  ['typescript', 'typescript'], ['ts', 'typescript'], ['tsx', 'typescript'],
  ['javascript', 'typescript'], ['js', 'typescript'], ['jsx', 'typescript'], ['mjs', 'typescript'],
  ['shellscript', 'shellscript'], ['bash', 'shellscript'], ['sh', 'shellscript'], ['shell', 'shellscript'],
  ['zsh', 'shellscript'], ['console', 'shellscript'],
  ['json', 'json'], ['jsonc', 'json'], ['json5', 'json'],
  ['py', 'python'], ['python', 'python'],
  ['rb', 'ruby'], ['ruby', 'ruby'],
  ['go', 'go'], ['golang', 'go'],
  ['rs', 'rust'], ['rust', 'rust'],
  ['java', 'java'],
  ['c', 'c'], ['h', 'c'],
  ['cpp', 'cpp'], ['c++', 'cpp'], ['cc', 'cpp'], ['hpp', 'cpp'],
  ['cs', 'csharp'], ['csharp', 'csharp'],
  ['kotlin', 'kotlin'], ['kt', 'kotlin'],
  ['swift', 'swift'],
  ['php', 'php'],
  ['yaml', 'yaml'], ['yml', 'yaml'],
  ['toml', 'toml'],
  ['ini', 'ini'], ['properties', 'ini'],
  ['md', 'markdown'], ['markdown', 'markdown'],
  ['html', 'html'], ['vue', 'html'],
  ['css', 'css'],
  ['scss', 'scss'],
  ['sql', 'sql'],
  ['xml', 'xml'], ['pom', 'xml'],
  ['lua', 'lua'],
]);

const cssVariablesTheme = createCssVariablesTheme({
  name: THEME_NAME,
  variablePrefix: '--shiki-',
  fontStyle: true,
});

let singleton: HighlighterCore | undefined;

function highlighter(): HighlighterCore {
  singleton ??= createHighlighterCoreSync({
    themes: [cssVariablesTheme],
    langs: [langTs, langBash, langJson],
    engine: createJavaScriptRegexEngine({ forgiving: true }),
  });
  return singleton;
}

const loaded = new Set<string>(['typescript', 'shellscript', 'json']);
const requested = new Set<string>();
const listeners = new Set<() => void>();

/** Resolve a fence/file hint to a grammar id, or `undefined` when unsupported. */
export function resolveLang(hint: string | undefined | null): string | undefined {
  if (!hint) return undefined;
  return LANG_ALIASES.get(hint.trim().toLowerCase());
}

export function supportsHighlighting(hint: string | undefined | null): boolean {
  return resolveLang(hint) !== undefined;
}

/** Subscribe to lazy grammar arrivals (re-render plain blocks). Returns the disposer. */
export function onGrammarLoaded(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** Kick off a lazy grammar load; resolves to whether the grammar is usable. */
export async function ensureGrammar(hint: string | undefined | null): Promise<boolean> {
  const lang = resolveLang(hint);
  if (!lang) return false;
  if (loaded.has(lang)) return true;
  const loader = LAZY_GRAMMARS.get(lang);
  if (!loader) return false;
  if (requested.has(lang)) {
    return new Promise((resolve) => {
      const off = onGrammarLoaded(() => {
        if (loaded.has(lang)) {
          off();
          resolve(true);
        }
      });
    });
  }
  requested.add(lang);
  try {
    const mod = await loader();
    highlighter().loadLanguageSync(mod.default);
    loaded.add(lang);
    listeners.forEach((l) => l());
    return true;
  } catch (e) {
    console.warn(`[highlight] failed loading grammar ${lang}`, e);
    requested.delete(lang);
    return false;
  }
}

/**
 * Highlight synchronously. Returns `null` when the grammar is unknown or not
 * loaded yet (callers render plain and retry after {@link ensureGrammar}).
 */
export function highlightToHtml(code: string, hint: string | undefined | null): string | null {
  const lang = resolveLang(hint);
  if (!lang || !loaded.has(lang)) return null;
  try {
    return highlighter().codeToHtml(code, { lang, theme: THEME_NAME, tokenizeTimeLimit: 500 });
  } catch (e) {
    console.warn('[highlight] tokenize failed', e);
    return null;
  }
}
