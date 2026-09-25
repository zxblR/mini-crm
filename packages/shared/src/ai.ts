export type AiSuggestionType =
  | 'INTENT_SCORE'
  | 'FOLLOW_UP_SUMMARY'
  | 'NEXT_ACTION'
  | 'SCRIPT'
  | 'WAKE_UP'

export const AI_POST_PATHS = {
  INTENT_SCORE: 'intent-score',
  FOLLOW_UP_SUMMARY: 'follow-up-summary',
  NEXT_ACTION: 'next-action',
  SCRIPT: 'script',
  WAKE_UP: 'wake-up',
} as const;

export const AI_GET_TYPES = {
  INTENT_SCORE: 'INTENT_SCORE',
  FOLLOW_UP_SUMMARY: 'FOLLOW_UP_SUMMARY',
  NEXT_ACTION: 'NEXT_ACTION',
  SCRIPT: 'SCRIPT',
  WAKE_UP: 'WAKE_UP',
} as const;

export interface GenerateAiSuggestionInput {
  forceRefresh: boolean;
}

export interface IntentScoreResult {
  score: number;
  level: 'low' | 'medium' | 'high';
  confidence: number;
  reasons: string[];
}

export interface FollowUpSummaryResult {
  summary: string;
  keyPoints: string[];
  risks: string[];
  openQuestions: string[];
}

export interface NextActionResult {
  action: string;
  rationale: string;
  priority: 'low' | 'medium' | 'high';
  dueInHours: number;
}

export interface ScriptResult {
  channel: 'phone' | 'email' | 'message';
  subject: string;
  script: string;
  personalizationNotes: string[];
}

export interface WakeUpResult {
  shouldWakeUp: boolean;
  reason: string;
  suggestedChannel: 'phone' | 'email' | 'message' | 'none';
  suggestedAtHours: number;
}

export type AiSuggestionResult =
  | IntentScoreResult
  | FollowUpSummaryResult
  | NextActionResult
  | ScriptResult
  | WakeUpResult;

export interface AiSuggestion<T = unknown> {
  id: string
  leadId: string
  type: AiSuggestionType
  result: T
  model: string
  promptVersion: string
  expiresAt: string
  cached: boolean
}
