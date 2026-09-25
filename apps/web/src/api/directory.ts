import type { ApiEnvelope, Tag, UserQuery, UserSummary } from '@mini-crm/shared';

import { http } from './http';
import {
  compactParams,
  type PagedData,
  unwrapPagedResponse,
} from './response';

export async function listUsers(query: UserQuery): Promise<PagedData<UserSummary>> {
  const response = await http.get<ApiEnvelope<UserSummary[]>>('/users', {
    params: compactParams(query),
  });
  return unwrapPagedResponse(response);
}

export async function listTags(page = 1, pageSize = 100): Promise<PagedData<Tag>> {
  const response = await http.get<ApiEnvelope<Tag[]>>('/tags', {
    params: { page, pageSize },
  });
  return unwrapPagedResponse(response);
}
