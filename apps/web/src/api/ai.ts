import type {
  AiSuggestion,
  AiSuggestionResult,
  AiSuggestionType,
  ApiEnvelope,
  GenerateAiSuggestionInput,
} from '@mini-crm/shared';
import { AI_GET_TYPES, AI_POST_PATHS } from '@mini-crm/shared';

import { http } from './http';
import { unwrapResponse } from './response';

export const AI_TYPES: AiSuggestionType[] = [
  AI_GET_TYPES.INTENT_SCORE,
  AI_GET_TYPES.FOLLOW_UP_SUMMARY,
  AI_GET_TYPES.NEXT_ACTION,
  AI_GET_TYPES.SCRIPT,
  AI_GET_TYPES.WAKE_UP,
];

export async function generateAiSuggestion(
  leadId: string,
  type: AiSuggestionType,
  input: GenerateAiSuggestionInput,
): Promise<AiSuggestion<AiSuggestionResult>> {
  const path = AI_POST_PATHS[type];
  const response = await http.post<ApiEnvelope<AiSuggestion<AiSuggestionResult>>>(
    '/leads/' + leadId + '/ai/' + path,
    input,
    { skipErrorToast: true, timeout: 15000 },
  );
  return unwrapResponse(response);
}

export async function getAiSuggestion(
  leadId: string,
  type: AiSuggestionType,
): Promise<AiSuggestion<AiSuggestionResult>> {
  const response = await http.get<ApiEnvelope<AiSuggestion<AiSuggestionResult>>>(
    '/leads/' + leadId + '/ai/' + type,
    { skipErrorToast: true, timeout: 15000 },
  );
  return unwrapResponse(response);
}
