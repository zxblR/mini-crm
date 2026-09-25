<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { LeadSummary } from '@mini-crm/shared';

import PageHeader from '@/components/common/PageHeader.vue';
import LeadBoardColumn from '@/components/leads/LeadBoardColumn.vue';
import LeadFormDialog from '@/components/leads/LeadFormDialog.vue';
import LeadStatusDialog from '@/components/leads/LeadStatusDialog.vue';
import { usePermissions } from '@/composables/usePermissions';
import { LEAD_STATUS_ORDER } from '@/constants/leads';
import { useLeadStore } from '@/stores/leads';

const store = useLeadStore();
const permissions = usePermissions();
const formOpen = ref(false);
const statusOpen = ref(false);
const statusLead = ref<LeadSummary | null>(null);

function changeStatus(lead: LeadSummary): void {
  statusLead.value = lead;
  statusOpen.value = true;
}

async function refresh(): Promise<void> {
  await store.fetchBoard();
}

onMounted(() => {
  void refresh().catch(() => undefined);
});
</script>

<template>
  <section>
    <PageHeader title="线索看板" description="按固定销售状态查看线索，并通过按钮完成状态迁移。">
      <template #actions>
        <el-button @click="$router.push('/leads')">返回列表</el-button>
        <el-button v-if="permissions.canCreateLead()" type="primary" @click="formOpen = true">新建线索</el-button>
      </template>
    </PageHeader>
    <div class="lead-board">
      <LeadBoardColumn
        v-for="status in LEAD_STATUS_ORDER"
        :key="status"
        :status="status"
        :items="store.board[status].items"
        :total="store.board[status].meta.total"
        :loading="store.board[status].loading"
        :error-message="store.board[status].errorMessage"
        @status="changeStatus"
        @load-more="store.fetchBoardStatus(status, true)"
      />
    </div>
    <LeadFormDialog v-model="formOpen" @saved="refresh" />
    <LeadStatusDialog v-model="statusOpen" :lead="statusLead" @changed="refresh" />
  </section>
</template>

<style scoped>
.lead-board { display: flex; gap: 16px; overflow-x: auto; padding-bottom: 16px; }
</style>
