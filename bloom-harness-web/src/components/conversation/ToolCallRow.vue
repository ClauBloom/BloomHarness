<script setup lang="ts">
import { computed, ref } from 'vue';
import type { ToolCallContent, ToolResultMessage } from '@/types/message.types';
import DsDisclosureRow from '@/components/primitives/DsDisclosureRow.vue';
import DsStateDot from '@/components/primitives/DsStateDot.vue';
import DsTerminalBlock from '@/components/primitives/DsTerminalBlock.vue';
import DsReadBlock from '@/components/primitives/DsReadBlock.vue';
import DsDiffBlock from '@/components/primitives/DsDiffBlock.vue';
import DsCodeBlock from '@/components/primitives/DsCodeBlock.vue';
import {
  IconBash,
  IconEditFile,
  IconRead,
  IconSearch,
  IconSkill,
  IconSparkle,
  ICON_STROKE,
} from '@/components/primitives/icons';
import { basename, parseExitCode, stripExitCode } from '@/utils/format';

/**
 * dsh ToolRow (GenericToolCard assembly): a 24px disclosure row — glyph, tool
 * title, 2×2 separator dot, summary — expanding to the tool-specific card
 * (terminal / read / diff / code) or an IN/OUT card.
 */
const props = defineProps<{
  call: ToolCallContent;
  result: ToolResultMessage | null;
  running: boolean;
  cwd?: string;
}>();

const open = ref(false);

type Variant = 'bash' | 'read' | 'write' | 'edit' | 'search' | 'skill' | 'others';

const variant = computed<Variant>(() => {
  switch (props.call.toolName) {
    case 'bash':
    case 'pwsh':
      return 'bash';
    case 'read':
      return 'read';
    case 'write':
      return 'write';
    case 'edit':
      return 'edit';
    case 'grep':
    case 'glob':
      return 'search';
    case 'skill':
      return 'skill';
    default:
      return 'others';
  }
});

const TITLES: Record<Variant, string> = {
  bash: 'Bash',
  read: '读取',
  write: '写入',
  edit: '编辑',
  search: '搜索',
  skill: '技能',
  others: '工具调用',
};

const ICONS = {
  bash: IconBash,
  read: IconRead,
  write: IconEditFile,
  edit: IconEditFile,
  search: IconSearch,
  skill: IconSkill,
  others: IconSparkle,
} as const;

const title = computed(() => (variant.value === 'others' ? props.call.toolName : TITLES[variant.value]));

const input = computed<Record<string, unknown>>(() =>
  props.call.input && typeof props.call.input === 'object' ? props.call.input : {},
);

function str(key: string): string | undefined {
  const v = input.value[key];
  return typeof v === 'string' && v.trim() ? v : undefined;
}

function relativize(path: string | undefined): string {
  if (!path) return '';
  const norm = (p: string) => p.replace(/\\/g, '/').replace(/\/+$/, '');
  const p = norm(path);
  const c = props.cwd ? norm(props.cwd) : '';
  if (c && (p === c || p.startsWith(c + '/'))) return p.slice(c.length + 1) || '.';
  return path;
}

const filePath = computed(() => str('path') ?? str('file_path'));

const outputText = computed(() => {
  if (!props.result) return '';
  return props.result.content
    .filter((c): c is { type: 'text'; text: string } => c.type === 'text')
    .map((c) => c.text)
    .join('\n');
});

const exitCode = computed(() => (variant.value === 'bash' ? parseExitCode(outputText.value) : null));

const state = computed<'running' | 'ok' | 'error'>(() => {
  if (props.running || !props.result) return 'running';
  if (props.result.isError || props.result.status === 'error') return 'error';
  if (exitCode.value !== null && exitCode.value !== 0) return 'error';
  return 'ok';
});

const summary = computed(() => {
  switch (variant.value) {
    case 'bash':
      return (str('command') ?? '').split('\n')[0] ?? '';
    case 'read':
    case 'write':
    case 'edit':
      return relativize(filePath.value);
    case 'search': {
      const pattern = str('pattern') ?? '';
      const path = str('path');
      return path ? `${pattern} · ${relativize(path)}` : pattern;
    }
    case 'skill':
      return str('name') ?? str('skill') ?? str('skill_name') ?? '';
    default: {
      try {
        const json = JSON.stringify(input.value);
        return json === '{}' ? '' : json.length > 120 ? `${json.slice(0, 120)}…` : json;
      } catch {
        return '';
      }
    }
  }
});

const failureLine = computed(() => {
  if (state.value !== 'error') return null;
  const first = stripExitCode(outputText.value).trim().split('\n')[0] ?? '';
  return first || '执行失败';
});

const summaryText = computed(() => failureLine.value ?? summary.value);

const diffStat = computed(() => {
  if (variant.value !== 'edit') return null;
  const oldLines = (str('old_string') ?? '').split('\n').length;
  const newLines = (str('new_string') ?? '').split('\n').length;
  return `+${newLines} -${oldLines}`;
});

const expandable = computed(() => Object.keys(input.value).length > 0 || outputText.value.length > 0);

const cwdLabel = computed(() => {
  const dir = str('workdir') ?? props.cwd;
  return dir ? basename(dir) || dir : '~';
});

const prettyInput = computed(() => {
  try {
    return JSON.stringify(input.value, null, 2);
  } catch {
    return String(input.value);
  }
});

const EXT_LANG: Record<string, string> = {
  ts: 'ts', tsx: 'tsx', js: 'js', jsx: 'jsx', mjs: 'js', json: 'json', java: 'java', kt: 'kotlin', py: 'py',
  rb: 'ruby', go: 'go', rs: 'rust', c: 'c', h: 'c', cpp: 'cpp', cc: 'cpp', hpp: 'cpp', cs: 'csharp',
  swift: 'swift', php: 'php', yml: 'yaml', yaml: 'yaml', toml: 'toml', ini: 'ini', properties: 'ini',
  md: 'md', html: 'html', vue: 'html', css: 'css', scss: 'scss', sql: 'sql', xml: 'xml', lua: 'lua',
  sh: 'sh', bash: 'sh', zsh: 'sh',
};

const fileLang = computed(() => {
  const p = filePath.value ?? '';
  const ext = p.split('.').pop()?.toLowerCase() ?? '';
  return EXT_LANG[ext];
});
</script>

<template>
  <div class="toolCallRow" :data-variant="variant" :data-tool="call.toolName" :data-state="state">
    <span v-if="state !== 'ok'" class="visually-hidden">{{ state === 'running' ? '运行中' : '失败' }}</span>
    <DsDisclosureRow :open="open" :expandable="expandable" :title="title" keep-content-when-open @toggle="open = !open">
      <template #icon>
        <DsStateDot v-if="state === 'error'" state="error" />
        <component :is="ICONS[variant]" v-else :size="14" :stroke-width="ICON_STROKE" />
      </template>
      <template #summary>
        <template v-if="summaryText">
          <span class="sep" aria-hidden="true" />
          <span :class="['summary', { errorSummary: failureLine !== null }]">{{ summaryText }}</span>
          <span v-if="diffStat && failureLine === null" class="summarySuffix diffStat">{{ diffStat }}</span>
        </template>
      </template>

      <div class="bodyWrap">
        <DsTerminalBlock
          v-if="variant === 'bash'"
          class="terminalBody"
          :command="str('command') ?? ''"
          :cwd="cwdLabel"
          :output="stripExitCode(outputText)"
          :exit-code="exitCode"
          :running="state === 'running'"
          :max-lines="Infinity"
          :output-max-height="224"
          compact
        />
        <DsReadBlock
          v-else-if="variant === 'read' && state !== 'running' && state !== 'error'"
          class="cardBody"
          :label="filePath ?? ''"
          :text="outputText"
        />
        <DsDiffBlock
          v-else-if="variant === 'edit'"
          class="cardBody"
          :path="relativize(filePath)"
          :old-text="str('old_string') ?? ''"
          :new-text="str('new_string') ?? ''"
        />
        <DsCodeBlock
          v-else-if="variant === 'write' && str('content')"
          class="cardBody"
          :code="str('content') ?? ''"
          :lang="fileLang"
          :label="relativize(filePath)"
          :max-height="260"
          small
        />
        <div v-else class="ioCard">
          <div v-if="Object.keys(input).length" class="ioSection">
            <span class="ioLabel">输入</span>
            <span class="ioText">{{ prettyInput }}</span>
          </div>
          <span v-if="Object.keys(input).length && (outputText || state === 'running')" class="ioDivider" aria-hidden="true" />
          <div v-if="outputText || state === 'running'" class="ioSection">
            <span class="ioLabel">输出</span>
            <span class="ioText" :data-error="state === 'error' || undefined">{{ state === 'running' && !outputText ? '运行中…' : outputText }}</span>
          </div>
        </div>
      </div>
    </DsDisclosureRow>
  </div>
</template>

<style scoped>
.toolCallRow {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.toolCallRow[data-state='running'] :deep(.row)::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 300px;
  background: linear-gradient(
    90deg,
    transparent 0%,
    color-mix(in srgb, var(--dsw-alias-bg-base) 60%, transparent) 55%,
    transparent 100%
  );
  animation: dsh-tool-row-sweep 2.6s ease-out infinite;
  pointer-events: none;
}

@keyframes dsh-tool-row-sweep {
  0% {
    left: -300px;
  }
  90%,
  100% {
    left: 100%;
  }
}

.sep {
  flex: none;
  width: 2px;
  height: 2px;
  border-radius: 1px;
  margin: 0 8px;
  background: var(--dsw-alias-label-caption);
}

.summary {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--dsh-content-font-size-secondary, 13px);
  line-height: calc(24px + var(--dsh-content-font-delta, 0px));
  color: var(--dsw-alias-label-tertiary);
}

.summarySuffix {
  flex: none;
  margin-left: 4px;
  white-space: nowrap;
  font-size: var(--dsh-content-font-size-secondary, 13px);
  line-height: calc(24px + var(--dsh-content-font-delta, 0px));
  color: var(--dsw-alias-label-tertiary);
}

.diffStat {
  margin-left: 10px;
  font-family: var(--ds-font-family-code);
  font-size: calc(var(--dsh-content-font-size-secondary, 13px) - 2px);
  color: var(--dsw-alias-label-caption);
  transform: translateY(0.5px);
}

.errorSummary {
  color: var(--dsw-alias-state-error-primary);
}

.bodyWrap {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.cardBody,
.terminalBody {
  margin: 4px 0 4px 4px;
}

.ioCard {
  display: flex;
  flex-direction: column;
  margin: 4px 0 4px 4px;
  border: 0.5px solid var(--dsw-alias-border-l1);
  border-radius: 12px;
  background: var(--dsw-alias-markdown-code-block);
  font: var(--dsw-font-markdown-code-block-small);
}

.ioSection {
  display: grid;
  grid-template-columns: max-content 1fr;
  column-gap: 14px;
  align-items: baseline;
  padding: 12px 16px;
  max-height: 150px;
  overflow-y: auto;
}

.ioLabel {
  position: sticky;
  top: 0;
  align-self: start;
  color: var(--dsw-alias-label-caption);
}

.ioDivider {
  flex: none;
  height: 0.5px;
  background: var(--dsw-alias-border-l2);
}

.ioText {
  min-width: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--dsw-alias-label-secondary);
}

.ioText[data-error] {
  color: var(--dsw-alias-state-error-primary);
}

@media (prefers-reduced-motion: reduce) {
  .toolCallRow[data-state='running'] :deep(.row)::after {
    animation: none;
  }
}
</style>
