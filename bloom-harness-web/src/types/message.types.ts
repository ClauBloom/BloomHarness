export type ThinkingLevel = 'off' | 'low' | 'medium' | 'high' | 'max';

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
}

export interface ToolCallContent {
  type: 'toolCall';
  toolCallId: string;
  toolName: string;
  input: Record<string, any>;
}

export type MessageContent = TextContent | ThinkingContent | ToolCallContent;

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
  timestamp: number;
  status: 'complete' | 'error';
  isError: boolean;
}

export type AgentMessage = UserMessage | AssistantMessage | ToolResultMessage;

export type TranscriptProgress = 
  | { type: 'item_started'; item: AgentMessage }
  | { type: 'assistant_delta'; messageId: string; contentIndex: number; kind: 'text' | 'thinking' | 'toolCall'; delta: string }
  | { type: 'item_updated'; item: AgentMessage }
  | { type: 'item_finished'; item: AgentMessage };
