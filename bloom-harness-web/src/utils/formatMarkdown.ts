import { Marked, Renderer, type Tokens } from 'marked';
import DOMPurify from 'dompurify';

/**
 * Markdown → sanitized HTML with the dsh DOM conventions (see styles/markdown.css):
 *   - fenced code → `div.md-code-block > .bannerWrap > .banner(infostring + copy) + pre > code[data-lang]`
 *     (syntax highlighting is applied afterwards by the hosting component via utils/highlight.ts)
 *   - tables → `div.tableScroll` (`md-table-wide` for ≥4 columns, else `tableFill`)
 *   - task lists → `ul.contains-task-list > li.task-list-item`
 *   - external links open in a new tab; non-http(s)/mailto links unwrap to text
 */

const ESCAPE_RE = /[&<>"']/g;
const ESCAPE_MAP: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
};

export function escapeHtml(text: string): string {
  return text.replace(ESCAPE_RE, (ch) => ESCAPE_MAP[ch] ?? ch);
}

const SAFE_HREF = /^(https?:\/\/|mailto:)/i;
const EXTERNAL_HREF = /^https?:\/\//i;

const COPY_LABEL = '复制';

function codeBlock(this: Renderer, { text, lang }: Tokens.Code): string {
  const info = (lang ?? '').trim().split(/\s+/)[0] ?? '';
  const safeLang = escapeHtml(info);
  const langAttr = info ? ` data-lang="${safeLang}"` : '';
  const codeClass = info ? ` class="language-${safeLang}"` : '';
  return (
    `<div class="md-code-block"${langAttr}>` +
    `<div class="bannerWrap"><div class="banner">` +
    `<div class="infostring">${safeLang}</div>` +
    `<div class="action"><button type="button" class="copyButton" data-md-copy>${COPY_LABEL}</button></div>` +
    `</div></div>` +
    `<pre class="plain"><code${codeClass}${langAttr}>${escapeHtml(text)}\n</code></pre>` +
    `</div>`
  );
}

function table(this: Renderer, token: Tokens.Table): string {
  const html = Renderer.prototype.table.call(this, token);
  const wide = token.header.length >= 4;
  const cls = wide ? 'tableScroll md-table-wide' : 'tableScroll tableFill';
  const tab = wide ? ' tabindex="0"' : '';
  return `<div class="${cls}"${tab}>${html}</div>`;
}

function list(this: Renderer, token: Tokens.List): string {
  const html = Renderer.prototype.list.call(this, token);
  if (!token.items.some((item) => item.task)) return html;
  return html.replace(/^<(ul|ol)([^>]*)>/, '<$1 class="contains-task-list"$2>');
}

function listitem(this: Renderer, item: Tokens.ListItem): string {
  const html = Renderer.prototype.listitem.call(this, item);
  return item.task ? html.replace(/^<li>/, '<li class="task-list-item">') : html;
}

function checkbox(this: Renderer, { checked }: Tokens.Checkbox): string {
  return `<input type="checkbox" disabled${checked ? ' checked' : ''}> `;
}

function link(this: Renderer, { href, title, tokens }: Tokens.Link): string {
  const text = this.parser.parseInline(tokens);
  if (!SAFE_HREF.test(href)) return text;
  const titleAttr = title ? ` title="${escapeHtml(title)}"` : '';
  const external = EXTERNAL_HREF.test(href) ? ' target="_blank" rel="noopener noreferrer"' : '';
  return `<a href="${escapeHtml(href)}"${titleAttr}${external}>${text}</a>`;
}

function image(this: Renderer, { href, title, text }: Tokens.Image): string {
  if (!EXTERNAL_HREF.test(href)) {
    return `<span class="imageAlt">${escapeHtml(text)}</span>`;
  }
  const titleAttr = title ? ` title="${escapeHtml(title)}"` : '';
  return `<img class="image" src="${escapeHtml(href)}" alt="${escapeHtml(text)}"${titleAttr} loading="lazy" decoding="async" referrerpolicy="no-referrer">`;
}

const instance = new Marked({
  gfm: true,
  breaks: false,
  renderer: {
    code: codeBlock,
    table,
    list,
    listitem,
    checkbox,
    link,
    image,
  },
});

const purifyConfig: Parameters<typeof DOMPurify.sanitize>[1] = {
  ADD_ATTR: ['target', 'referrerpolicy'],
  FORBID_TAGS: ['style', 'script', 'iframe', 'object', 'embed', 'form'],
};

export function renderMarkdown(markdown: string): string {
  if (!markdown) return '';
  const rawHtml = instance.parse(markdown, { async: false }) as string;
  return DOMPurify.sanitize(rawHtml, purifyConfig);
}
