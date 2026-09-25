<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { TaskItem, TaskQuery } from '@mini-crm/shared';

import AsyncPageState from '@/components/common/AsyncPageState.vue';
import PageHeader from '@/components/common/PageHeader.vue';
import PaginationBar from '@/components/common/PaginationBar.vue';
import TaskEditDialog from '@/components/tasks/TaskEditDialog.vue';
import TaskFilterPanel from '@/components/tasks/TaskFilterPanel.vue';
import TaskResolveDialog from '@/components/tasks/TaskResolveDialog.vue';
import TaskTable from '@/components/tasks/TaskTable.vue';
import { usePermissions } from '@/composables/usePermissions';
import { useDirectoryStore } from '@/stores/directory';
import { useTaskStore } from '@/stores/tasks';

const store = useTaskStore();
const directory = useDirectoryStore();
const permissions = usePermissions();
const activeTab = ref('today');
const filters = ref<TaskQuery>({ page: 1, pageSize: 20 });
const editOpen = ref(false);
const resolveOpen = ref(false);
const selectedTask = ref<TaskItem | null>(null);
const resolveAction = ref<'complete' | 'cancel'>('complete');

function edit(task: TaskItem): void { selectedTask.value = task; editOpen.value = true; }
function resolve(task: TaskItem, action: 'complete' | 'cancel'): void { selectedTask.value = task; resolveAction.value = action; resolveOpen.value = true; }

async function loadAll(query: TaskQuery = filters.value): Promise<void> {
  filters.value = query;
  await store.fetchAll(query).catch(() => undefined);
}

async function pageToday(page: number, pageSize: number): Promise<void> {
  await store.fetchToday(page, pageSize).catch(() => undefined);
}

async function pageAll(page: number, pageSize: number): Promise<void> {
  await loadAll({ ...filters.value, page, pageSize });
}

onMounted(async () => {
  if (permissions.isManager.value) await directory.loadOwners().catch(() => undefined);
  await Promise.all([store.fetchToday().catch(() => undefined), loadAll()]);
});
</script>

<template>
  <section>
    <PageHeader title="任务" description="查看今日待办和全部任务，完成或取消自己的任务。" />
    <el-tabs v-model="activeTab">
      <el-tab-pane label="今日任务" name="today">
        <el-card shadow="never">
          <AsyncPageState :loading="store.todayLoading" :error-message="store.todayError" :empty="store.todayItems.length === 0" empty-description="今天没有待处理任务" @retry="pageToday(store.todayMeta.page, store.todayMeta.pageSize)">
            <TaskTable :items="store.todayItems" :loading="store.todayLoading" @edit="edit" @resolve="resolve" />
            <PaginationBar :page="store.todayMeta.page" :page-size="store.todayMeta.pageSize" :total="store.todayMeta.total" @change="pageToday" />
          </AsyncPageState>
        </el-card>
      </el-tab-pane>
      <el-tab-pane label="全部任务" name="all">
        <TaskFilterPanel v-model="filters" :owners="directory.owners" :show-assignee-filter="permissions.isManager.value" @search="loadAll" />
        <el-card shadow="never">
          <AsyncPageState :loading="store.loading" :error-message="store.errorMessage" :empty="store.items.length === 0" empty-description="没有符合条件的任务" @retry="loadAll()">
            <TaskTable :items="store.items" :loading="store.loading" @edit="edit" @resolve="resolve" />
            <PaginationBar :page="store.meta.page" :page-size="store.meta.pageSize" :total="store.meta.total" @change="pageAll" />
          </AsyncPageState>
        </el-card>
      </el-tab-pane>
    </el-tabs>
    <TaskEditDialog v-model="editOpen" :task="selectedTask" :owners="directory.owners" />
    <TaskResolveDialog v-model="resolveOpen" :task="selectedTask" :action="resolveAction" />
  </section>
</template>
