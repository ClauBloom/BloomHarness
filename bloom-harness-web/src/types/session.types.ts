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
  queuedSteer: UserMessage[];
  queuedSteerCount: number;
}

export interface ServerSnapshot {
  serverId: string;
  protocolVersion: number;
  revision: number;
  sessions: SessionMetadata[];
  models: Array<{
    provider: string;
    id: string;
    name: string;
  }>;
}
