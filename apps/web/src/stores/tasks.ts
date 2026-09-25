import { reactive, ref } from 'vue';
import { defineStore } from 'pinia';
import type {
  PaginationMeta,
  ResolveTaskInput,
  TaskItem,
  TaskQuery,
  UpdateTaskInput,
} from '@mini-crm/shared';

import { normalizeApiError } from '@/api/http';
import {
  cancelTask,
  completeTask,
  listTasks,
  listTodayTasks,
  updateTask,
} from '@/api/tasks';

function emptyMeta(): PaginationMeta {
  return { page: 1, pageSize: 20, total: 0 };
}

export const useTaskStore = defineStore('tasks', () => {
  const todayItems = ref<TaskItem[]>([]);
  const todayMeta = reactive<PaginationMeta>(emptyMeta());
  const todayLoading = ref(false);
  const todayError = ref<string | null>(null);
  const items = ref<TaskItem[]>([]);
  const meta = reactive<PaginationMeta>(emptyMeta());
  const query = ref<TaskQuery>({ page: 1, pageSize: 20 });
  const loading = ref(false);
  const errorMessage = ref<string | null>(null);

  async function fetchToday(page = 1, pageSize = 20): Promise<void> {
    todayLoading.value = true;
    todayError.value = null;
    try {
      const result = await listTodayTasks({ page, pageSize });
      todayItems.value = result.items;
      Object.assign(todayMeta, result.meta);
    } catch (error) {
      todayError.value = normalizeApiError(error).message;
      throw error;
    } finally {
      todayLoading.value = false;
    }
  }

  async function fetchAll(overrides: Partial<TaskQuery> = {}): Promise<void> {
    query.value = { ...query.value, ...overrides };
    loading.value = true;
    errorMessage.value = null;
    try {
      const result = await listTasks(query.value);
      items.value = result.items;
      Object.assign(meta, result.meta);
    } catch (error) {
      errorMessage.value = normalizeApiError(error).message;
      throw error;
    } finally {
      loading.value = false;
    }
  }

  async function edit(id: string, input: UpdateTaskInput): Promise<void> {
    await updateTask(id, input);
    await Promise.all([
      fetchAll().catch(() => undefined),
      fetchToday(todayMeta.page, todayMeta.pageSize).catch(() => undefined),
    ]);
  }

  async function resolve(
    id: string,
    action: 'complete' | 'cancel',
    input: ResolveTaskInput,
  ): Promise<void> {
    if (action === 'complete') await completeTask(id, input);
    else await cancelTask(id, input);
    await Promise.all([
      fetchAll().catch(() => undefined),
      fetchToday(todayMeta.page, todayMeta.pageSize).catch(() => undefined),
    ]);
  }

  return {
    todayItems,
    todayMeta,
    todayLoading,
    todayError,
    items,
    meta,
    query,
    loading,
    errorMessage,
    fetchToday,
    fetchAll,
    edit,
    resolve,
  };
});
