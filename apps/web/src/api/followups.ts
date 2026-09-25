import type {
  ApiEnvelope,
  CreateFollowUpInput,
  FollowUp,
  FollowUpQuery,
  UpdateFollowUpInput,
} from '@mini-crm/shared';

import { http } from './http';
import {
  compactParams,
  type PagedData,
  unwrapPagedResponse,
  unwrapResponse,
} from './response';

export async function listFollowUps(
  leadId: string,
  query: FollowUpQuery,
): Promise<PagedData<FollowUp>> {
  const response = await http.get<ApiEnvelope<FollowUp[]>>(
    '/leads/' + leadId + '/follow-ups',
    { params: compactParams(query) },
  );
  return unwrapPagedResponse(response);
}

export async function createFollowUp(
  leadId: string,
  input: CreateFollowUpInput,
): Promise<FollowUp> {
  const response = await http.post<ApiEnvelope<FollowUp>>(
    '/leads/' + leadId + '/follow-ups',
    input,
    { skipErrorToast: true },
  );
  return unwrapResponse(response);
}

export async function updateFollowUp(
  id: string,
  input: UpdateFollowUpInput,
): Promise<FollowUp> {
  const response = await http.patch<ApiEnvelope<FollowUp>>(
    '/follow-ups/' + id,
    input,
    { skipErrorToast: true },
  );
  return unwrapResponse(response);
}

export async function deleteFollowUp(id: string): Promise<{ deleted: boolean; id: string }> {
  const response = await http.delete<ApiEnvelope<{ deleted: boolean; id: string }>>(
    '/follow-ups/' + id,
    { skipErrorToast: true },
  );
  return unwrapResponse(response);
}
