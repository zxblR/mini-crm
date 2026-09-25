import type {
  ApiEnvelope,
  ResolveTaskInput,
  TaskItem,
  TaskQuery,
  TodayTaskQuery,
  UpdateTaskInput,
} from '@mini-crm/shared';

import { http } from './http';
import {
  compactParams,
  type PagedData,
  unwrapPagedResponse,
  unwrapResponse,
} from './response';

export async function listTasks(query: TaskQuery): Promise<PagedData<TaskItem>> {
  const response = await http.get<ApiEnvelope<TaskItem[]>>('/tasks', {
    params: compactParams(query),
  });
  return unwrapPagedResponse(response);
}

export async function listTodayTasks(query: TodayTaskQuery): Promise<PagedData<TaskItem>> {
  const response = await http.get<ApiEnvelope<TaskItem[]>>('/tasks/today', {
    params: compactParams(query),
  });
  return unwrapPagedResponse(response);
}

export async function updateTask(id: string, input: UpdateTaskInput): Promise<TaskItem> {
  const response = await http.patch<ApiEnvelope<TaskItem>>('/tasks/' + id, input, {
    skipErrorToast: true,
  });
  return unwrapResponse(response);
}

export async function completeTask(id: string, input: ResolveTaskInput): Promise<TaskItem> {
  const response = await http.post<ApiEnvelope<TaskItem>>('/tasks/' + id + '/complete', input, {
    skipErrorToast: true,
  });
  return unwrapResponse(response);
}

export async function cancelTask(id: string, input: ResolveTaskInput): Promise<TaskItem> {
  const response = await http.post<ApiEnvelope<TaskItem>>('/tasks/' + id + '/cancel', input, {
    skipErrorToast: true,
  });
  return unwrapResponse(response);
}
