import { reactive } from 'vue';
import type { SessionSnapshot } from '@/types/session.types';

/**
 * Client-side session titles. The backend currently stores `sessionName = id`
 * (the POST /api/sessions `name` is not persisted), so the shell keeps its own
 * title map: the name derived from the first prompt when a session is created
 * here, or learned from the first user message of a loaded snapshot.
 */
const STORAGE_KEY = 'bloom.session-titles.v1';
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const TITLE_MAX = 40;

function load(): Record<string, string> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const parsed = raw ? (JSON.parse(raw) as Record<string, string>) : {};
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

const titles = reactive<Record<string, string>>(load());

function persist() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(titles));
  } catch {
    // storage unavailable
  }
}

export function isPlaceholderName(name: string | undefined, id: string): boolean {
  const n = name?.trim();
  return !n || n === id || UUID_RE.test(n);
}

/** Derive a title from prompt text (first line, ≤40 chars). */
export function titleFromText(text: string): string {
  const firstLine = text.trim().split(/\r?\n/)[0]?.replace(/\s+/g, ' ').trim() ?? '';
  if (!firstLine) return '';
  const chars = Array.from(firstLine);
  return chars.length > TITLE_MAX ? chars.slice(0, TITLE_MAX).join('') + '…' : firstLine;
}

export function rememberSessionTitle(id: string, title: string) {
  const t = title.trim();
  if (!id || !t) return;
  titles[id] = t;
  persist();
}

export function sessionTitle(meta: { id: string; sessionName?: string; name?: string }): string {
  const cached = titles[meta.id];
  if (cached) return cached;
  const name = meta.sessionName ?? meta.name;
  return isPlaceholderName(name, meta.id) ? '新会话' : name!.trim();
}

/** Learn a title from the first user message of a snapshot when none is known. */
export function learnSessionTitle(snapshot: SessionSnapshot) {
  if (titles[snapshot.id]) return;
  if (!isPlaceholderName(snapshot.name, snapshot.id)) {
    rememberSessionTitle(snapshot.id, snapshot.name!);
    return;
  }
  const firstUser = snapshot.transcript.find((m) => m.role === 'user');
  if (!firstUser) return;
  const text = firstUser.content
    .filter((c): c is { type: 'text'; text: string } => c.type === 'text')
    .map((c) => c.text)
    .join('\n');
  const title = titleFromText(text);
  if (title) rememberSessionTitle(snapshot.id, title);
}

export function useSessionTitles() {
  return { titles, sessionTitle, rememberSessionTitle, learnSessionTitle, titleFromText };
}
