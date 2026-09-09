import { computed } from 'vue';
import { useAgentStore } from '@/stores/agentStore';
import type {
  AgentMessage,
  AssistantMessage,
  ToolCallContent,
  ToolResultMessage,
  Usage,
  UserMessage,
} from '@/types/message.types';

/**
 * Chat flow assembly (port of the dsh ChatView node list): the transcript plus
 * the live streaming buffer become a flat list of sibling flow items. A "turn"
 * is the run of items sharing `turn`.
 *
 * Backend specifics handled here (see SessionEventBridge):
 *  - the complete assistant message arrives after the LLM call; deltas live in
 *    the store's streaming buffer until then;
 *  - each tool call is echoed live as a synthetic assistant message whose id is
 *    the toolCallId — tool calls are therefore de-duplicated by toolCallId;
 *  - tool results are separate `role: 'tool'` messages paired by toolCallId.
 */
export type FlowItem =
  | { kind: 'user'; key: string; turn: number; message: UserMessage; text: string; isLatest: boolean }
  | {
      kind: 'assistant-step';
      key: string;
      turn: number;
      message: AssistantMessage | null;
      thinking: string;
      text: string;
      streaming: boolean;
      interrupted: boolean;
    }
  | {
      kind: 'tool-call';
      key: string;
      turn: number;
      call: ToolCallContent;
      result: ToolResultMessage | null;
      /** True while no result has arrived and the session is still running. */
      running: boolean;
    }
  | { kind: 'turn-error'; key: string; turn: number; message: AssistantMessage }
  | {
      kind: 'turn-tail';
      key: string;
      turn: number;
      /** Aggregated assistant text of the turn (copy target). */
      text: string;
      usage: Usage | null;
      timestamp: number;
      isLatestTurn: boolean;
    };

export interface TurnStats {
  turns: number;
  steps: number;
  inputTokens: number;
  outputTokens: number;
}

function textOf(message: AgentMessage): string {
  return message.content
    .filter((c): c is { type: 'text'; text: string } => c.type === 'text')
    .map((c) => c.text)
    .join('\n');
}

const THINK_RE = /<think>([\s\S]*?)(?:<\/think>|$)/i;

/**
 * Some models inline their reasoning as `<think>…</think>` inside the text
 * block; split it out so it renders as a reasoning row.
 */
export function splitInlineThink(text: string): { thinking: string; text: string } {
  const m = THINK_RE.exec(text);
  if (!m) return { thinking: '', text };
  return { thinking: m[1]!.trim(), text: text.replace(THINK_RE, '').trim() };
}

function sumUsage(a: Usage | null, b: Usage | undefined): Usage | null {
  if (!b) return a;
  if (!a) return { ...b };
  return {
    input: a.input + b.input,
    output: a.output + b.output,
    cacheRead: a.cacheRead + b.cacheRead,
    cacheWrite: a.cacheWrite + b.cacheWrite,
    reasoning: (a.reasoning ?? 0) + (b.reasoning ?? 0),
    totalTokens: a.totalTokens + b.totalTokens,
  };
}

export function buildFlow(
  transcript: AgentMessage[],
  streaming: { text: string; thinking: string },
  running: boolean,
): { items: FlowItem[]; stats: TurnStats } {
  const items: FlowItem[] = [];
  const results = new Map<string, ToolResultMessage>();
  for (const m of transcript) {
    if (m.role === 'tool') results.set(m.toolCallId, m);
  }

  const seenCalls = new Set<string>();
  let turn = 0;
  let steps = 0;
  let turnText: string[] = [];
  let turnUsage: Usage | null = null;
  let turnHasAssistant = false;
  let turnTimestamp = 0;
  let inputTokens = 0;
  let outputTokens = 0;
  let lastUserIndex = -1;

  const closeTurn = (isLatest: boolean) => {
    if (turnHasAssistant) {
      items.push({
        kind: 'turn-tail',
        key: `tail:${turn}`,
        turn,
        text: turnText.join('\n\n'),
        usage: turnUsage,
        timestamp: turnTimestamp,
        isLatestTurn: isLatest,
      });
    }
    turnText = [];
    turnUsage = null;
    turnHasAssistant = false;
    turnTimestamp = 0;
  };

  const pushCall = (call: ToolCallContent) => {
    if (seenCalls.has(call.toolCallId)) return;
    seenCalls.add(call.toolCallId);
    const result = results.get(call.toolCallId) ?? null;
    items.push({
      kind: 'tool-call',
      key: `call:${call.toolCallId}`,
      turn,
      call,
      result,
      running: !result && running,
    });
  };

  for (const m of transcript) {
    if (m.role === 'user') {
      closeTurn(false);
      turn += 1;
      const item: FlowItem = {
        kind: 'user',
        key: `user:${m.id}`,
        turn,
        message: m,
        text: textOf(m),
        isLatest: false,
      };
      items.push(item);
      lastUserIndex = items.length - 1;
      continue;
    }
    if (m.role === 'tool') continue;

    // assistant
    const calls = m.content.filter((c): c is ToolCallContent => c.type === 'toolCall');
    const synthetic = calls.length === 1 && m.content.length === 1 && calls[0]!.toolCallId === m.id;
    if (synthetic) {
      pushCall(calls[0]!);
      continue;
    }

    if (turn === 0) turn = 1;
    steps += 1;
    turnHasAssistant = true;
    turnTimestamp = m.timestamp;
    turnUsage = sumUsage(turnUsage, m.usage);
    if (m.usage) {
      inputTokens += m.usage.input ?? 0;
      outputTokens += m.usage.output ?? 0;
    }

    const explicitThinking = m.content
      .filter((c): c is { type: 'thinking'; thinking: string } => c.type === 'thinking')
      .map((c) => c.thinking)
      .join('\n');
    const rawText = textOf(m);
    const inline = splitInlineThink(rawText);
    const thinking = [explicitThinking, inline.thinking].filter(Boolean).join('\n');
    const text = inline.text;
    if (text) turnText.push(text);

    if (thinking || text || (calls.length === 0 && m.status !== 'error')) {
      items.push({
        kind: 'assistant-step',
        key: `step:${m.id}`,
        turn,
        message: m,
        thinking,
        text,
        streaming: false,
        interrupted: m.status === 'aborted' || m.stopReason === 'aborted',
      });
    }
    for (const call of calls) pushCall(call);
    if (m.status === 'error') {
      items.push({ kind: 'turn-error', key: `error:${m.id}`, turn, message: m });
    }
  }

  if (lastUserIndex >= 0) {
    const u = items[lastUserIndex];
    if (u && u.kind === 'user') u.isLatest = true;
  }

  const hasStreaming = running && (streaming.text.length > 0 || streaming.thinking.length > 0);
  if (hasStreaming) {
    if (turn === 0) turn = 1;
    const inline = splitInlineThink(streaming.text);
    items.push({
      kind: 'assistant-step',
      key: 'step:streaming',
      turn,
      message: null,
      thinking: [streaming.thinking, inline.thinking].filter(Boolean).join('\n'),
      text: inline.text,
      streaming: true,
      interrupted: false,
    });
  }

  if (!running) closeTurn(true);

  return { items, stats: { turns: turn, steps, inputTokens, outputTokens } };
}

export function useChatFlow() {
  const store = useAgentStore();
  const flow = computed(() =>
    buildFlow(
      store.transcript,
      { text: store.streamingText, thinking: store.activeThinking },
      store.isRunning,
    ),
  );
  return {
    items: computed(() => flow.value.items),
    stats: computed(() => flow.value.stats),
  };
}
