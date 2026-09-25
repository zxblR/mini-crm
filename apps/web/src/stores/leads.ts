import { reactive, ref } from 'vue';
import { defineStore } from 'pinia';
import type {
  ChangeLeadStatusRequest,
  CreateLeadRequest,
  LeadDetail,
  LeadQuery,
  LeadSummary,
  PaginationMeta,
  UpdateLeadRequest,
} from '@mini-crm/shared';
import { LeadStatus, SortOrder } from '@mini-crm/shared';

import {
  archiveLead,
  changeLeadStatus,
  createLead,
  getLead,
  listLeads,
  restoreLead,
  updateLead,
} from '@/api/leads';
import { normalizeApiError } from '@/api/http';

interface BoardColumnState {
  items: LeadSummary[];
  meta: PaginationMeta;
  loading: boolean;
  errorMessage: string | null;
}

function emptyMeta(pageSize = 20): PaginationMeta {
  return { page: 1, pageSize, total: 0 };
}

function emptyColumn(): BoardColumnState {
  return { items: [], meta: emptyMeta(100), loading: false, errorMessage: null };
}

export const useLeadStore = defineStore('leads', () => {
  const items = ref<LeadSummary[]>([]);
  const detail = ref<LeadDetail | null>(null);
  const query = ref<LeadQuery>({
    page: 1,
    pageSize: 20,
    sortBy: 'updatedAt',
    sortOrder: SortOrder.Desc,
  });
  const meta = reactive<PaginationMeta>(emptyMeta());
  const loading = ref(false);
  const detailLoading = ref(false);
  const errorMessage = ref<string | null>(null);
  const board = reactive<Record<LeadStatus, BoardColumnState>>({
    [LeadStatus.New]: emptyColumn(),
    [LeadStatus.Contacted]: emptyColumn(),
    [LeadStatus.Qualified]: emptyColumn(),
    [LeadStatus.Proposal]: emptyColumn(),
    [LeadStatus.Negotiation]: emptyColumn(),
    [LeadStatus.Won]: emptyColumn(),
    [LeadStatus.Lost]: emptyColumn(),
  });

  async function fetchList(overrides: Partial<LeadQuery> = {}): Promise<void> {
    query.value = { ...query.value, ...overrides };
    loading.value = true;
    errorMessage.value = null;
    try {
      const result = await listLeads(query.value);
      items.value = result.items;
      Object.assign(meta, result.meta);
    } catch (error) {
      errorMessage.value = normalizeApiError(error).message;
      throw error;
    } finally {
      loading.value = false;
    }
  }

  async function fetchDetail(id: string): Promise<LeadDetail> {
    detailLoading.value = true;
    if (detail.value?.id !== id) detail.value = null;
    try {
      detail.value = await getLead(id, { timelinePage: 1, timelinePageSize: 100 });
      return detail.value;
    } finally {
      detailLoading.value = false;
    }
  }

  async function saveLead(
    input: CreateLeadRequest | UpdateLeadRequest,
    id?: string,
  ): Promise<LeadSummary> {
    const saved = id
      ? await updateLead(id, input as UpdateLeadRequest)
      : await createLead(input as CreateLeadRequest);
    if (detail.value?.id === saved.id) await fetchDetail(saved.id);
    return saved;
  }

  async function setStatus(
    lead: LeadSummary,
    input: ChangeLeadStatusRequest,
  ): Promise<void> {
    await changeLeadStatus(lead.id, input);
    if (detail.value?.id === lead.id) await fetchDetail(lead.id);
  }

  async function setArchived(lead: LeadSummary, archived: boolean): Promise<void> {
    if (archived) await archiveLead(lead.id);
    else await restoreLead(lead.id);
    if (detail.value?.id === lead.id) await fetchDetail(lead.id);
  }

  async function fetchBoardStatus(status: LeadStatus, append = false): Promise<void> {
    const column = board[status];
    const page = append ? column.meta.page + 1 : 1;
    column.loading = true;
    column.errorMessage = null;
    try {
      const result = await listLeads({
        page,
        pageSize: 100,
        status,
        archived: false,
        sortBy: 'updatedAt',
        sortOrder: SortOrder.Desc,
      });
      column.items = append ? [...column.items, ...result.items] : result.items;
      column.meta = result.meta;
    } catch (error) {
      column.errorMessage = normalizeApiError(error).message;
      throw error;
    } finally {
      column.loading = false;
    }
  }

  async function fetchBoard(): Promise<void> {
    await Promise.all(Object.values(LeadStatus).map((status) => fetchBoardStatus(status)));
  }

  return {
    items,
    detail,
    query,
    meta,
    loading,
    detailLoading,
    errorMessage,
    board,
    fetchList,
    fetchDetail,
    saveLead,
    setStatus,
    setArchived,
    fetchBoardStatus,
    fetchBoard,
  };
});
