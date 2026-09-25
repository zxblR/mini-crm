<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { FollowUp, LeadSummary } from '@mini-crm/shared';
import { FOLLOW_UP_TYPE_LABELS } from '@mini-crm/shared';

import { deleteFollowUp, listFollowUps } from '@/api/followups';
import { normalizeApiError } from '@/api/http';
import AsyncPageState from '@/components/common/AsyncPageState.vue';
import PaginationBar from '@/components/common/PaginationBar.vue';
import { usePermissions } from '@/composables/usePermissions';
import { formatDateTime } from '@/utils/format';
import FollowUpFormDialog from './FollowUpFormDialog.vue';

const props = defineProps<{ lead: LeadSummary }>();
const emit = defineEmits<{ changed: [] }>();
const permissions = usePermissions();
const items = ref<FollowUp[]>([]);
const loading = ref(false);
const errorMessage = ref<string | null>(null);
const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const dialogOpen = ref(false);
const editing = ref<FollowUp | null>(null);

async function load(): Promise<void> {
  loading.value = true;
  errorMessage.value = null;
  try {
    const result = await listFollowUps(props.lead.id, { page: page.value, pageSize: pageSize.value });
    items.value = result.items;
    page.value = result.meta.page;
    pageSize.value = result.meta.pageSize;
    total.value = result.meta.total;
  } catch (error) {
    errorMessage.value = normalizeApiError(error).message;
  } finally {
    loading.value = false;
  }
}

function createItem(): void {
  editing.value = null;
  dialogOpen.value = true;
}

function editItem(item: FollowUp): void {
  editing.value = item;
  dialogOpen.value = true;
}

async function removeItem(item: FollowUp): Promise<void> {
  try {
    await ElMessageBox.confirm('删除后该记录不会再显示，确定继续吗？', '删除跟进记录', { type: 'warning' });
    await deleteFollowUp(item.id);
    ElMessage.success('跟进记录已删除');
    await load();
    emit('changed');
  } catch (error) {
    if (error === 'cancel' || error === 'close') return;
    ElMessage.error(normalizeApiError(error).message);
  }
}

async function changePage(nextPage: number, nextPageSize: number): Promise<void> {
  page.value = nextPage;
  pageSize.value = nextPageSize;
  await load();
}

async function saved(): Promise<void> {
  await load();
  emit('changed');
}

onMounted(load);
</script>

<template>
  <el-card shadow="never">
    <template #header><div class="card-header"><strong>跟进记录</strong><el-button v-if="permissions.canCreateFollowUp(lead)" type="primary" @click="createItem">新增跟进</el-button></div></template>
    <AsyncPageState :loading="loading" :error-message="errorMessage" :empty="items.length === 0" empty-description="暂无跟进记录" @retry="load">
      <div class="follow-up-list">
        <article v-for="item in items" :key="item.id" class="follow-up-item">
          <div class="follow-up-item__header"><div><el-tag effect="plain">{{ FOLLOW_UP_TYPE_LABELS[item.type] }}</el-tag><strong>{{ item.summary }}</strong></div><span>{{ formatDateTime(item.occurredAt) }}</span></div>
          <p v-if="item.result">{{ item.result }}</p>
          <small>记录人：{{ item.createdBy.name }}<template v-if="item.nextStepAt"> · 下一步：{{ formatDateTime(item.nextStepAt) }}</template></small>
          <div class="follow-up-item__actions"><el-button v-if="permissions.canEditFollowUp(item, lead)" link type="primary" @click="editItem(item)">编辑</el-button><el-button v-if="permissions.canDeleteFollowUp(item)" link type="danger" @click="removeItem(item)">删除</el-button></div>
        </article>
      </div>
      <PaginationBar :page="page" :page-size="pageSize" :total="total" @change="changePage" />
    </AsyncPageState>
  </el-card>
  <FollowUpFormDialog v-model="dialogOpen" :lead-id="lead.id" :follow-up="editing" @saved="saved" />
</template>

<style scoped>
.card-header, .follow-up-item__header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.follow-up-list { display: grid; gap: 12px; }
.follow-up-item { padding: 16px; border: 1px solid #e2e8f0; border-radius: 6px; }
.follow-up-item__header > div { display: flex; align-items: center; gap: 10px; }
.follow-up-item__header span, small { color: #94a3b8; font-size: 12px; }
.follow-up-item p { color: #475569; white-space: pre-wrap; }
.follow-up-item__actions { margin-top: 8px; text-align: right; }
</style>
