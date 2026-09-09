import { ref } from 'vue';

/** dsh copy feedback: label flips to "复制成功" for `holdMs` after a successful write. */
export function useCopyFeedback(holdMs = 1000) {
  const copied = ref(false);
  let timer: ReturnType<typeof setTimeout> | null = null;

  async function copy(text: string): Promise<boolean> {
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(text);
      } else {
        const area = document.createElement('textarea');
        area.value = text;
        area.style.position = 'fixed';
        area.style.opacity = '0';
        document.body.appendChild(area);
        area.select();
        document.execCommand('copy');
        area.remove();
      }
      copied.value = true;
      if (timer) clearTimeout(timer);
      timer = setTimeout(() => {
        copied.value = false;
        timer = null;
      }, holdMs);
      return true;
    } catch (e) {
      console.warn('copy failed', e);
      return false;
    }
  }

  return { copied, copy };
}

export const COPY_LABEL = '复制';
export const COPIED_LABEL = '复制成功';
