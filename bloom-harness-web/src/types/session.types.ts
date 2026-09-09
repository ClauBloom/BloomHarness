import { AgentMessage, ModelRef, ThinkingLevel, UserMessage } from './message.types';

export type SessionPhase = 'idle' | 'turn' | 'compaction' | 'branch_summary' | 'retry';

export interface SessionMetadata {
  id: string;
  createdAt: number;
  updatedAt?: number;
  parentSessionId?: string;
  sessionName?: string;
  cwd?: string;
}

export interface SessionSnapshot {
  id: string;
  name?: string;
  cwd: string;
  createdAt: number;
  updatedAt: number;
  phase: SessionPhase;
  model: ModelRef;
  thinkingLevel: ThinkingLevel;
  attached: boolean;
  locked: boolean;
  revision: number;
  transcript: AgentMessage[];
  /** Backend SessionSnapshot fields */
  steeringQueue?: UserMessage[];
  steeringCount?: number;
  /** Frontend legacy compatibility aliases */
  queuedSteer?: UserMessage[];
  queuedSteerCount?: number;
}

/** `GET /api/config/providers` row. */
export interface ProviderConfig {
  providerId: string;
  name: string;
  baseUrl: string;
  /** Masked unless requested with `reveal=true`; '' when unconfigured. */
  apiKey: string;
  protocol: 'openai' | 'anthropic';
  models: string[];
  isConfigured: boolean;
}

/** `GET /api/config/general` model. */
export interface GeneralSettings {
  defaultProvider: string;
  defaultModel: string;
  temperature: number;
  maxTokens: number;
  systemPrompt: string;
}

/** `GET /api/workspace/current`. */
export interface WorkspaceInfo {
  defaultWorkspace: string;
  userHome: string;
  os: string;
  fileSeparator: string;
}

/** `GET /api/workspace/browse`. */
export interface DirectoryListing {
  currentPath: string;
  parentPath: string;
  directories: Array<{ name: string; path: string; isReadable: boolean; isWritable: boolean }>;
}
