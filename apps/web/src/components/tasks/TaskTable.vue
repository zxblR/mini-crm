<script setup lang="ts">
import type { TaskItem } from '@mini-crm/shared';
import { RoleCode, TaskStatus } from '@mini-crm/shared';

import { useAuthStore } from '@/stores/auth';
import { formatDateTime } from '@/utils/format';

defineProps<{ items: TaskItem[]; loading: boolean }>();
const emit = defineEmits<{
  edit: [task: TaskItem];
  resolve: [task: TaskItem, action: 'complete' | 'cancel'];
}>();
const auth = useAuthStore();

function canManage(task: TaskItem): boolean {
  if (auth.hasAnyRole([RoleCode.Owner, RoleCode.Admin])) return true;
  return auth.hasRole(RoleCode.Sales) && task.assignee.id === auth.user?.id;
}

function statusLabel(task: TaskItem): string {
  if (task.isOverdue && task.status === TaskStatus.Pending) return '已逾期';
  if (task.status === TaskStatus.Completed) return '已完成';
  if (task.status === TaskStatus.Cancelled) return '已取消';
  return '待处理';
}

function statusType(task: TaskItem): 'warning' | 'danger' | 'success' | 'info' {
  if (task.isOverdue && task.status === TaskStatus.Pending) return 'danger';
  if (task.status === TaskStatus.Completed) return 'success';
  if (task.status === TaskStatus.Cancelled) return 'info';
  return 'warning';
}
</script>

<template>
  <el-table v-loading="loading" :data="items" row-key="id">
    <el-table-column label="任务" min-width="220">
      <template #default="{ row }"><strong>{{ row.title }}</strong><div class="secondary"><router-link :to="'/leads/' + row.leadId">{{ row.leadName }}</router-link></div></template>
    </el-table-column>
    <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="statusType(row)" effect="plain">{{ statusLabel(row) }}</el-tag></template></el-table-column>
    <el-table-column label="负责人" width="130"><template #default="{ row }">{{ row.assignee.name }}</template></el-table-column>
    <el-table-column label="截止时间" width="180"><template #default="{ row }">{{ formatDateTime(row.dueAt) }}</template></el-table-column>
    <el-table-column label="处理说明" min-width="160"><template #default="{ row }">{{ row.resolutionNote || '—' }}</template></el-table-column>
    <el-table-column label="操作" width="190" fixed="right">
      <template #default="{ row }">
        <template v-if="row.status === TaskStatus.Pending && canManage(row)">
          <el-button link type="primary" @click="emit('edit', row)">编辑</el-button>
          <el-button link type="success" @click="emit('resolve', row, 'complete')">完成</el-button>
          <el-button link type="danger" @click="emit('resolve', row, 'cancel')">取消</el-button>
        </template>
        <span v-else class="secondary">只读</span>
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.secondary { margin-top: 4px; color: #94a3b8; font-size: 12px; }
.secondary a { color: #2563eb; text-decoration: none; }
</style>
