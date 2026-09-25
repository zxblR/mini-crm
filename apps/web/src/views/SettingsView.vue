<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { UserQuery, UserSummary } from '@mini-crm/shared';

import { normalizeApiError } from '@/api/http';
import { listMembers, setMemberActive } from '@/api/users';
import AsyncPageState from '@/components/common/AsyncPageState.vue';
import PageHeader from '@/components/common/PageHeader.vue';
import PaginationBar from '@/components/common/PaginationBar.vue';
import MemberFilterBar from '@/components/settings/MemberFilterBar.vue';
import MemberFormDialog from '@/components/settings/MemberFormDialog.vue';
import MemberTable from '@/components/settings/MemberTable.vue';

const items = ref<UserSummary[]>([]);
const filters = ref<UserQuery>({ page: 1, pageSize: 20 });
const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const loading = ref(false);
const errorMessage = ref<string | null>(null);
const formOpen = ref(false);

async function load(query: UserQuery = filters.value): Promise<void> {
  filters.value = query;
  loading.value = true;
  errorMessage.value = null;
  try {
    const result = await listMembers(query);
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

async function setStatus(member: UserSummary, isActive: boolean): Promise<void> {
  try {
    await ElMessageBox.confirm(isActive ? '确定启用该成员吗？' : '停用后该成员的刷新令牌将失效，确定继续吗？', isActive ? '启用成员' : '停用成员', { type: 'warning' });
    await setMemberActive(member.id, isActive);
    ElMessage.success(isActive ? '成员已启用' : '成员已停用');
    await load();
  } catch (error) {
    if (error === 'cancel' || error === 'close') return;
    const failure = normalizeApiError(error);
    if (failure.code === 'LAST_ADMIN_REQUIRED') {
      await ElMessageBox.alert(failure.message, '无法停用成员', { type: 'error' });
    } else {
      ElMessage.error(failure.message);
    }
  }
}

async function changePage(nextPage: number, nextPageSize: number): Promise<void> {
  await load({ ...filters.value, page: nextPage, pageSize: nextPageSize });
}

onMounted(load);
</script>

<template>
  <section>
    <PageHeader title="团队成员" description="新增成员并管理当前组织成员的启用状态。"><template #actions><el-button type="primary" @click="formOpen = true">新增成员</el-button></template></PageHeader>
    <MemberFilterBar v-model="filters" @search="load" />
    <el-card shadow="never">
      <AsyncPageState :loading="loading" :error-message="errorMessage" :empty="items.length === 0" empty-description="暂无团队成员" @retry="load()">
        <MemberTable :items="items" :loading="loading" @status="setStatus" />
        <PaginationBar :page="page" :page-size="pageSize" :total="total" @change="changePage" />
      </AsyncPageState>
    </el-card>
    <MemberFormDialog v-model="formOpen" @saved="load()" />
  </section>
</template>
