export type ThinkingLevel = 'off' | 'minimal' | 'low' | 'medium' | 'high' | 'xhigh' | 'max';

export interface ModelRef {
  provider: string;
  id: string;
}

export interface TextContent {
  type: 'text';
  text: string;
}

export interface ThinkingContent {
  type: 'thinking';
  thinking: string;
  redacted?: boolean;
}

export interface ToolCallContent {
  type: 'toolCall';
  toolCallId: string;
  toolName: string;
  input: Record<string, any>;
}

export interface ImageContent {
  type: 'image';
  /** Base64 payload. */
  data: string;
  mimeType: string;
}

export type MessageContent = TextContent | ThinkingContent | ToolCallContent | ImageContent;

export interface UsageCost {
  input: number;
  output: number;
  cacheRead: number;
  cacheWrite: number;
  total: number;
}

export interface Usage {
  input: number;
  output: number;
  cacheRead: number;
  cacheWrite: number;
  reasoning?: number;
  totalTokens: number;
  cost?: UsageCost;
}

export interface UserMessage {
  id: string;
  role: 'user';
  content: MessageContent[];
  timestamp: number;
}

export interface AssistantMessage {
  id: string;
  role: 'assistant';
  content: MessageContent[];
  model?: ModelRef;
  responseModel?: string;
  usage?: Usage;
  timestamp: number;
  status: 'streaming' | 'complete' | 'error' | 'aborted';
  stopReason?: 'stop' | 'length' | 'toolUse' | 'error' | 'aborted';
  errorMessage?: string;
}

export interface ToolResultMessage {
  id: string;
  role: 'tool';
  toolCallId: string;
  toolName: string;
  input?: any;
  content: MessageContent[];
  details?: any;
  usage?: Usage;
  timestamp: number;
  status: 'running' | 'complete' | 'error';
  isError: boolean;
}

export type AgentMessage = UserMessage | AssistantMessage | ToolResultMessage;

export type TranscriptProgress =
  | { type: 'item_started'; item: AgentMessage }
  | { type: 'assistant_delta'; messageId: string; contentIndex: number; kind: 'text' | 'thinking' | 'toolCall'; delta: string }
  | { type: 'item_updated'; item: AgentMessage }
  | { type: 'item_finished'; item: AgentMessage };
