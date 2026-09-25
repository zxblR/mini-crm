<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import type { LeadSummary, StatsOverviewDTO, TaskItem } from '@mini-crm/shared';
import { SortOrder } from '@mini-crm/shared';

import { getStatsOverview } from '@/api/dashboard';
import { normalizeApiError } from '@/api/http';
import { listLeads } from '@/api/leads';
import { listTodayTasks } from '@/api/tasks';
import AsyncPageState from '@/components/common/AsyncPageState.vue';
import PageHeader from '@/components/common/PageHeader.vue';
import OverviewCards from '@/components/dashboard/OverviewCards.vue';
import RecentLeadPanel from '@/components/dashboard/RecentLeadPanel.vue';
import TodayTaskPanel from '@/components/dashboard/TodayTaskPanel.vue';

const overview = ref<StatsOverviewDTO | null>(null);
const tasks = ref<TaskItem[]>([]);
const taskTotal = ref(0);
const leads = ref<LeadSummary[]>([]);
const loading = ref(false);
const errorMessage = ref<string | null>(null);
const empty = computed(() => !overview.value && tasks.value.length === 0 && leads.value.length === 0);

async function load(): Promise<void> {
  loading.value = true;
  errorMessage.value = null;
  try {
    const [overviewResult, taskResult, leadResult] = await Promise.all([
      getStatsOverview({}),
      listTodayTasks({ page: 1, pageSize: 10 }),
      listLeads({
        page: 1,
        pageSize: 5,
        sortBy: 'updatedAt',
        sortOrder: SortOrder.Desc,
      }),
    ]);
    overview.value = overviewResult;
    tasks.value = taskResult.items;
    taskTotal.value = taskResult.meta.total;
    leads.value = leadResult.items;
  } catch (error) {
    errorMessage.value = normalizeApiError(error).message;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <section>
    <PageHeader title="工作台" description="聚焦今天需要推进的任务和最近变化的线索。" />
    <AsyncPageState
      :loading="loading"
      :error-message="errorMessage"
      :empty="empty"
      empty-description="暂无工作台数据"
      @retry="load"
    >
      <OverviewCards v-if="overview" :overview="overview" />
      <div class="workspace-grid">
        <TodayTaskPanel :items="tasks" :total="taskTotal" />
        <RecentLeadPanel :items="leads" />
      </div>
    </AsyncPageState>
  </section>
</template>

<style scoped>
.workspace-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; margin-top: 20px; }
@media (max-width: 900px) { .workspace-grid { grid-template-columns: 1fr; } }
</style>
