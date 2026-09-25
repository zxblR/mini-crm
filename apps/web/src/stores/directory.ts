import { ref } from 'vue';
import { defineStore } from 'pinia';
import type { Tag, UserSummary } from '@mini-crm/shared';
import { RoleCode } from '@mini-crm/shared';

import { listTags, listUsers } from '@/api/directory';
import { useAuthStore } from '@/stores/auth';

export const useDirectoryStore = defineStore('directory', () => {
  const owners = ref<UserSummary[]>([]);
  const tags = ref<Tag[]>([]);
  const loadingOwners = ref(false);
  const loadingTags = ref(false);

  async function loadOwners(force = false): Promise<void> {
    if (owners.value.length > 0 && !force) return;
    loadingOwners.value = true;
    try {
      const result = await listUsers({ page: 1, pageSize: 100, isActive: true });
      owners.value = result.items;
    } finally {
      loadingOwners.value = false;
    }
  }

  async function loadTags(force = false): Promise<void> {
    if (tags.value.length > 0 && !force) return;
    loadingTags.value = true;
    try {
      const result = await listTags();
      tags.value = result.items;
    } finally {
      loadingTags.value = false;
    }
  }

  async function loadLeadDirectories(): Promise<void> {
    const auth = useAuthStore();
    const requests: Promise<void>[] = [loadTags()];
    if (auth.hasAnyRole([RoleCode.Owner, RoleCode.Admin])) {
      requests.push(loadOwners());
    }
    await Promise.all(requests);
  }

  return {
    owners,
    tags,
    loadingOwners,
    loadingTags,
    loadOwners,
    loadTags,
    loadLeadDirectories,
  };
});
