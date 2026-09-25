import type {
  ApiEnvelope,
  CreateUserInput,
  UserQuery,
  UserSummary,
} from '@mini-crm/shared';

import { http } from './http';
import {
  compactParams,
  type PagedData,
  unwrapPagedResponse,
  unwrapResponse,
} from './response';

export async function listMembers(query: UserQuery): Promise<PagedData<UserSummary>> {
  const response = await http.get<ApiEnvelope<UserSummary[]>>('/users', {
    params: compactParams(query),
  });
  return unwrapPagedResponse(response);
}

export async function createMember(input: CreateUserInput): Promise<UserSummary> {
  const response = await http.post<ApiEnvelope<UserSummary>>('/users', input, {
    skipErrorToast: true,
  });
  return unwrapResponse(response);
}

export async function setMemberActive(id: string, isActive: boolean): Promise<UserSummary> {
  const response = await http.patch<ApiEnvelope<UserSummary>>(
    '/users/' + id + '/status',
    { isActive },
    { skipErrorToast: true },
  );
  return unwrapResponse(response);
}
