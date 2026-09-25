import type {
  ApiEnvelope,
  ChangeLeadStatusRequest,
  ChangeLeadStatusResponse,
  CreateLeadRequest,
  LeadArchiveResponse,
  LeadDetail,
  LeadQuery,
  LeadSummary,
  LeadTimelineQuery,
  UpdateLeadRequest,
} from '@mini-crm/shared';

import { http } from './http';
import {
  compactParams,
  type PagedData,
  unwrapPagedResponse,
  unwrapResponse,
} from './response';

export async function listLeads(query: LeadQuery): Promise<PagedData<LeadSummary>> {
  const response = await http.get<ApiEnvelope<LeadSummary[]>>('/leads', {
    params: compactParams(query),
  });
  return unwrapPagedResponse(response);
}

export async function getLead(
  id: string,
  query: LeadTimelineQuery = {},
): Promise<LeadDetail> {
  const response = await http.get<ApiEnvelope<LeadDetail>>('/leads/' + id, {
    params: compactParams(query),
  });
  return unwrapResponse(response);
}

export async function createLead(input: CreateLeadRequest): Promise<LeadSummary> {
  const response = await http.post<ApiEnvelope<LeadSummary>>('/leads', input, {
    skipErrorToast: true,
  });
  return unwrapResponse(response);
}

export async function updateLead(
  id: string,
  input: UpdateLeadRequest,
): Promise<LeadSummary> {
  const response = await http.patch<ApiEnvelope<LeadSummary>>('/leads/' + id, input, {
    skipErrorToast: true,
  });
  return unwrapResponse(response);
}

export async function changeLeadStatus(
  id: string,
  input: ChangeLeadStatusRequest,
): Promise<ChangeLeadStatusResponse> {
  const response = await http.post<ApiEnvelope<ChangeLeadStatusResponse>>(
    '/leads/' + id + '/status',
    input,
    { skipErrorToast: true },
  );
  return unwrapResponse(response);
}

export async function archiveLead(id: string): Promise<LeadArchiveResponse> {
  const response = await http.post<ApiEnvelope<LeadArchiveResponse>>(
    '/leads/' + id + '/archive',
  );
  return unwrapResponse(response);
}

export async function restoreLead(id: string): Promise<LeadArchiveResponse> {
  const response = await http.post<ApiEnvelope<LeadArchiveResponse>>(
    '/leads/' + id + '/restore',
  );
  return unwrapResponse(response);
}
