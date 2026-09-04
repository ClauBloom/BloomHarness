import { marked } from 'marked';
import DOMPurify from 'dompurify';

export function renderMarkdown(markdown: string): string {
  if (!markdown) return '';
  const rawHtml = marked.parse(markdown, { async: false }) as string;
  return DOMPurify.sanitize(rawHtml);
}
