/** Formatting helpers shared by the shell (paths, relative time, clocks, tokens). */

const SEPARATORS = /[\\/]+/;

/** Last path segment; handles `/` and `\`, trailing separators and drive roots. */
export function basename(path: string | null | undefined): string {
  if (!path) return '';
  const trimmed = path.replace(/[\\/]+$/, '');
  if (!trimmed) return path;
  const parts = trimmed.split(SEPARATORS).filter(Boolean);
  return parts[parts.length - 1] ?? trimmed;
}

/** Replace the home-directory prefix with `~` (dsh hover-card path style). */
export function abbreviateHome(path: string, home?: string | null): string {
  if (!path || !home) return path;
  const norm = (p: string) => p.replace(/\\/g, '/').replace(/\/+$/, '');
  const p = norm(path);
  const h = norm(home);
  if (h && (p === h || p.startsWith(h + '/'))) return '~' + p.slice(h.length);
  return path;
}

/** Split a path into its ancestry (root → leaf), keeping the separator style. */
export function pathSegments(path: string): { label: string; path: string }[] {
  const sep = path.includes('\\') && !path.includes('/') ? '\\' : '/';
  const clean = path.replace(/[\\/]+$/, '');
  if (!clean) return [{ label: path, path }];
  const parts = clean.split(SEPARATORS);
  const out: { label: string; path: string }[] = [];
  let acc = '';
  parts.forEach((part, i) => {
    if (i === 0) {
      // Drive root (`C:`) or POSIX root ('')
      acc = part === '' ? sep : part + sep;
      out.push({ label: part === '' ? sep : part + sep, path: acc });
      return;
    }
    if (!part) return;
    acc = acc.endsWith(sep) ? acc + part : acc + sep + part;
    out.push({ label: part, path: acc });
  });
  return out;
}

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;
const MONTH = 30 * DAY;
const YEAR = 365 * DAY;

/** Compact relative time used by session rows (dsh `time.*` zh strings). */
export function relativeTime(timestamp: number | undefined, now = Date.now()): string {
  if (!timestamp) return '';
  const diff = Math.max(0, now - timestamp);
  if (diff < MINUTE) return '刚刚';
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)}分钟`;
  if (diff < DAY) return `${Math.floor(diff / HOUR)}小时`;
  if (diff < MONTH) return `${Math.floor(diff / DAY)}天`;
  if (diff < YEAR) return `${Math.floor(diff / MONTH)}个月`;
  return `${Math.floor(diff / YEAR)}年`;
}

const pad2 = (n: number) => String(n).padStart(2, '0');

/** dsh `formatMessageClock`: HH:mm today, M月D日 HH:mm this year, else with year. */
export function formatClock(timestamp: number, now = new Date()): string {
  const d = new Date(timestamp);
  const time = `${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
  if (d.toDateString() === now.toDateString()) return time;
  if (d.getFullYear() === now.getFullYear()) return `${d.getMonth() + 1}月${d.getDate()}日 ${time}`;
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 ${time}`;
}

/** `12.3K` / `1.2M` token abbreviations. */
export function formatTokens(n: number | undefined | null): string {
  if (n === undefined || n === null || !Number.isFinite(n)) return '0';
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(n >= 10_000_000 ? 0 : 1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(n >= 10_000 ? 0 : 1)}K`;
  return String(Math.round(n));
}

/** `12s` / `1m 05s` run durations. */
export function formatDuration(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  if (minutes === 0) return `${seconds}s`;
  return `${minutes}m ${pad2(seconds)}s`;
}

/** Trailing `[exit code: N]` marker BashTool appends to failing output. */
export function parseExitCode(output: string): number | null {
  const m = /\[exit code: (-?\d+)\]\s*$/.exec(output);
  return m ? Number(m[1]) : null;
}

export function stripExitCode(output: string): string {
  return output.replace(/\n?\[exit code: -?\d+\]\s*$/, '');
}
