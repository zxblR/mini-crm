import { ref, shallowRef } from 'vue';

import type { PagedData } from '@/api/response';

export function usePagedQuery<T>() {
  const items = shallowRef<T[]>([]);
  const page = ref(1);
  const pageSize = ref(20);
  const total = ref(0);
  const loading = ref(false);
  const errorMessage = ref<string | null>(null);

  async function load(request: () => Promise<PagedData<T>>): Promise<void> {
    loading.value = true;
    errorMessage.value = null;
    try {
      const result = await request();
      items.value = result.items;
      page.value = result.meta.page;
      pageSize.value = result.meta.pageSize;
      total.value = result.meta.total;
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '加载失败';
      throw error;
    } finally {
      loading.value = false;
    }
  }

  return { items, page, pageSize, total, loading, errorMessage, load };
}
