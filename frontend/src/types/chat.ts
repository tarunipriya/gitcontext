import type { AskResponse, ProblemDetail } from '../api/client';

export type MessageRole = 'user' | 'assistant';

export interface ChatMessage {
  id: string;
  role: MessageRole;
  content: string;
  createdAt: string;
  pending?: boolean;
  statusText?: string;
  error?: ProblemDetail;
  response?: AskResponse;
  revealed?: boolean;
}

export interface Chat {
  id: string;
  title: string;
  repo: string;
  messages: ChatMessage[];
  createdAt: string;
  updatedAt: string;
}
