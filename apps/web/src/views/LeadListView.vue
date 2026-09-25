<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import type { LeadQuery, LeadSummary } from '@mini-crm/shared';
import { LeadStatus, SortOrder } from '@mini-crm/shared';

import { normalizeApiError } from '@/api/http';
import AsyncPageState from '@/components/common/AsyncPageState.vue';
import PageHeader from '@/components/common/PageHeader.vue';
import PaginationBar from '@/components/common/PaginationBar.vue';
import LeadFilterPanel from '@/components/leads/LeadFilterPanel.vue';
import LeadFormDialog from '@/components/leads/LeadFormDialog.vue';
import LeadStatusDialog from '@/components/leads/LeadStatusDialog.vue';
import LeadTable from '@/components/leads/LeadTable.vue';
import { usePermissions } from '@/composables/usePermissions';
import { useRouteFilters } from '@/composables/useRouteFilters';
import { LEAD_STATUS_ORDER } from '@/constants/leads';
import { useDirectoryStore } from '@/stores/directory';
import { useLeadStore } from '@/stores/leads';

const store = useLeadStore();
const directory = useDirectoryStore();
const permissions = usePermissions();
const routeFilters = useRouteFilters();
const formOpen = ref(false);
const statusOpen = ref(false);
const editingLead = ref<LeadSummary | null>(null);
const statusLead = ref<LeadSummary | null>(null);
const filters = ref<LeadQuery>(readFilters());
const showOwnerFilter = computed(() => permissions.isManager.value);

function readFilters(): LeadQuery {
  const rawStatus = routeFilters.stringValue('status');
  const status = LEAD_STATUS_ORDER.find((item) => item === rawStatus);
  const rawSortBy = routeFilters.stringValue('sortBy');
  const sortBy = ['createdAt', 'updatedAt', 'nextFollowUpAt', 'company'].includes(rawSortBy ?? '')
    ? (rawSortBy as LeadQuery['sortBy'])
    : 'updatedAt';
  const requestedPageSize = routeFilters.numberValue('pageSize', 20);
  const pageSize = [20, 50, 100].includes(requestedPageSize) ? requestedPageSize : 20;
  return {
    page: routeFilters.numberValue('page', 1),
    pageSize,
    keyword: routeFilters.stringValue('keyword'),
    status: status as LeadStatus | undefined,
    ownerId: routeFilters.stringValue('ownerId'),
    source: routeFilters.stringValue('source'),
    archived: routeFilters.booleanValue('archived'),
    sortBy,
    sortOrder: routeFilters.stringValue('sortOrder') === SortOrder.Asc ? SortOrder.Asc : SortOrder.Desc,
  };
}

async function syncAndLoad(query: LeadQuery): Promise<void> {
  filters.value = query;
  await routeFilters.replaceQuery(query);
  await store.fetchList(query).catch(() => undefined);
}

async function reset(): Promise<void> {
  await syncAndLoad({ page: 1, pageSize: 20, sortBy: 'updatedAt', sortOrder: SortOrder.Desc });
}

function createLead(): void {
  editingLead.value = null;
  formOpen.value = true;
}

function editLead(lead: LeadSummary): void {
  editingLead.value = lead;
  formOpen.value = true;
}

function changeStatus(lead: LeadSummary): void {
  statusLead.value = lead;
  statusOpen.value = true;
}

async function setArchived(lead: LeadSummary, archived: boolean): Promise<void> {
  try {
    await ElMessageBox.confirm(
      archived ? '归档后线索将从默认列表隐藏，确定继续吗？' : '确定恢复该线索吗？',
      archived ? '归档线索' : '恢复线索',
      { type: 'warning' },
    );
    await store.setArchived(lead, archived);
    ElMessage.success(archived ? '线索已归档' : '线索已恢复');
    await store.fetchList(filters.value);
  } catch (error) {
    if (error === 'cancel' || error === 'close') return;
    ElMessage.error(normalizeApiError(error).message);
  }
}

async function changePage(page: number, pageSize: number): Promise<void> {
  await syncAndLoad({ ...filters.value, page, pageSize });
}

async function refresh(): Promise<void> {
  await store.fetchList(filters.value).catch(() => undefined);
}

onMounted(async () => {
  if (permissions.isManager.value) await directory.loadOwners().catch(() => undefined);
  await store.fetchList(filters.value).catch(() => undefined);
});
</script>

<template>
  <section>
    <PageHeader title="线索" description="搜索、筛选并推进当前可见范围内的客户线索。">
      <template #actions>
        <el-button @click="$router.push('/leads/board')">状态看板</el-button>
        <el-button v-if="permissions.canCreateLead()" type="primary" @click="createLead">新建线索</el-button>
      </template>
    </PageHeader>
    <LeadFilterPanel v-model="filters" :owners="directory.owners" :show-owner-filter="showOwnerFilter" @search="syncAndLoad" @reset="reset" />
    <el-card shadow="never">
      <AsyncPageState :loading="store.loading && store.items.length === 0" :error-message="store.errorMessage" :empty="store.items.length === 0" empty-description="没有符合条件的线索" @retry="refresh">
        <LeadTable :items="store.items" :loading="store.loading" @edit="editLead" @status="changeStatus" @archive="setArchived" />
        <PaginationBar :page="store.meta.page" :page-size="store.meta.pageSize" :total="store.meta.total" @change="changePage" />
      </AsyncPageState>
    </el-card>
    <LeadFormDialog v-model="formOpen" :lead="editingLead" @saved="refresh" />
    <LeadStatusDialog v-model="statusOpen" :lead="statusLead" @changed="refresh" />
  </section>
</template>
