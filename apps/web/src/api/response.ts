import type { AxiosResponse } from 'axios';
import type { ApiEnvelope, ApiMeta, PaginationMeta } from '@mini-crm/shared';

export interface PagedData<T> {
  items: T[];
  meta: PaginationMeta;
}

export function unwrapResponse<T>(response: AxiosResponse<ApiEnvelope<T>>): T {
  return response.data.data;
}

export function unwrapPagedResponse<T>(
  response: AxiosResponse<ApiEnvelope<T[]>>,
): PagedData<T> {
  return {
    items: response.data.data,
    meta: paginationMeta(response.data.meta),
  };
}

export function paginationMeta(meta: ApiMeta): PaginationMeta {
  return {
    ...meta,
    page: meta.page ?? 1,
    pageSize: meta.pageSize ?? 20,
    total: meta.total ?? 0,
  };
}

export function compactParams<T extends object>(params: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => {
      if (value === undefined || value === null || value === '') return false;
      if (Array.isArray(value)) return value.length > 0;
      return true;
    }),
  ) as Partial<T>;
}
