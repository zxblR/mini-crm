<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { normalizeApiError } from '@/api/http';
import AsyncPageState from '@/components/common/AsyncPageState.vue';
import PageHeader from '@/components/common/PageHeader.vue';
import AiSuggestionPanel from '@/components/ai/AiSuggestionPanel.vue';
import FollowUpList from '@/components/followups/FollowUpList.vue';
import LeadFormDialog from '@/components/leads/LeadFormDialog.vue';
import LeadInfoCard from '@/components/leads/LeadInfoCard.vue';
import LeadStatusDialog from '@/components/leads/LeadStatusDialog.vue';
import LeadTimeline from '@/components/leads/LeadTimeline.vue';
import { usePermissions } from '@/composables/usePermissions';
import { useLeadStore } from '@/stores/leads';
import { leadDisplayName } from '@/utils/format';

const route = useRoute();
const router = useRouter();
const store = useLeadStore();
const permissions = usePermissions();
const formOpen = ref(false);
const statusOpen = ref(false);
const errorMessage = ref<string | null>(null);
const leadId = computed(() => String(route.params.id));

async function load(): Promise<void> {
  errorMessage.value = null;
  try {
    await store.fetchDetail(leadId.value);
  } catch (error) {
    errorMessage.value = normalizeApiError(error).message;
  }
}

async function setArchived(archived: boolean): Promise<void> {
  if (!store.detail) return;
  try {
    await ElMessageBox.confirm(
      archived ? '确定归档该线索吗？' : '确定恢复该线索吗？',
      archived ? '归档线索' : '恢复线索',
      { type: 'warning' },
    );
    await store.setArchived(store.detail, archived);
    ElMessage.success(archived ? '线索已归档' : '线索已恢复');
  } catch (error) {
    if (error === 'cancel' || error === 'close') return;
    ElMessage.error(normalizeApiError(error).message);
  }
}

onMounted(load);
watch(leadId, () => {
  void load();
});
</script>

<template>
  <section>
    <PageHeader :title="store.detail ? leadDisplayName(store.detail.name, store.detail.company) : '线索详情'" description="查看基础信息、跟进记录和客户时间线。">
      <template #actions>
        <el-button @click="router.push('/leads')">返回列表</el-button>
        <template v-if="store.detail">
          <el-button v-if="permissions.canEditLead(store.detail)" @click="formOpen = true">编辑</el-button>
          <el-button v-if="permissions.canChangeLeadStatus(store.detail)" type="primary" @click="statusOpen = true">切换状态</el-button>
          <el-button v-if="!store.detail.archivedAt && permissions.canArchiveLead(store.detail)" type="danger" plain @click="setArchived(true)">归档</el-button>
          <el-button v-else-if="store.detail.archivedAt && permissions.canRestoreLead()" type="primary" plain @click="setArchived(false)">恢复</el-button>
        </template>
      </template>
    </PageHeader>
    <AsyncPageState :loading="store.detailLoading" :error-message="errorMessage" :empty="!store.detail" empty-description="线索不存在或无权查看" @retry="load">
      <div v-if="store.detail" class="detail-grid">
        <div class="detail-main">
          <LeadInfoCard :lead="store.detail" />
          <FollowUpList :lead="store.detail" @changed="load" />
          <AiSuggestionPanel :lead="store.detail" />
        </div>
        <LeadTimeline :events="store.detail.timeline" />
      </div>
    </AsyncPageState>
    <LeadFormDialog v-if="store.detail" v-model="formOpen" :lead="store.detail" @saved="load" />
    <LeadStatusDialog v-if="store.detail" v-model="statusOpen" :lead="store.detail" @changed="load" />
  </section>
</template>

<style scoped>
.detail-grid { display: grid; grid-template-columns: minmax(0, 2fr) minmax(320px, 1fr); gap: 20px; align-items: start; }
.detail-main { display: grid; gap: 20px; }
@media (max-width: 960px) { .detail-grid { grid-template-columns: 1fr; } }
</style>
