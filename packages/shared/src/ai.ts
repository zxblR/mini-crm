export type AiSuggestionType =
  | 'INTENT_SCORE'
  | 'FOLLOW_UP_SUMMARY'
  | 'NEXT_ACTION'
  | 'SCRIPT'
  | 'WAKE_UP'

export interface AiLeadContext {
  leadId: string
  customerText?: string
  recentFollowUps?: string[]
  stage?: string
  ownerId?: string
}

export interface AiRequest {
  leadId: string
  forceRefresh?: boolean
  context?: AiLeadContext
}

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

export type AiApiError = {
  ok: false
  error: { code: string; message: string }
}

export type AiApiSuccess<T> = { ok: true; data: T; meta?: Record<string, unknown> }

export type AiApiResult<T> = AiApiSuccess<T> | AiApiError
