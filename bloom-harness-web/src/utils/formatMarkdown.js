import { marked } from 'marked';
import DOMPurify from 'dompurify';
export function renderMarkdown(markdown) {
    if (!markdown)
        return '';
    const rawHtml = marked.parse(markdown, { async: false });
    return DOMPurify.sanitize(rawHtml);
}
