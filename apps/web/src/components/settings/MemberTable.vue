<script setup lang="ts">
import type { UserSummary } from '@mini-crm/shared';
import { ROLE_LABELS } from '@mini-crm/shared';

import { formatDateTime } from '@/utils/format';

defineProps<{ items: UserSummary[]; loading: boolean }>();
const emit = defineEmits<{ status: [member: UserSummary, isActive: boolean] }>();
</script>

<template>
  <el-table v-loading="loading" :data="items" row-key="id">
    <el-table-column label="成员" min-width="180"><template #default="{ row }"><strong>{{ row.name }}</strong><div class="secondary">{{ row.email || row.phone || '无联系方式' }}</div></template></el-table-column>
    <el-table-column label="角色" min-width="180"><template #default="{ row }: { row: UserSummary }"><div class="role-list"><el-tag v-for="role in row.roleCodes" :key="role" effect="plain">{{ ROLE_LABELS[role] }}</el-tag></div></template></el-table-column>
    <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.isActive ? 'success' : 'info'" effect="plain">{{ row.isActive ? '启用' : '停用' }}</el-tag></template></el-table-column>
    <el-table-column label="最近登录" width="180"><template #default="{ row }">{{ formatDateTime(row.lastLoginAt) }}</template></el-table-column>
    <el-table-column label="创建时间" width="180"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
    <el-table-column label="操作" width="110" fixed="right"><template #default="{ row }"><el-button link :type="row.isActive ? 'danger' : 'primary'" @click="emit('status', row, !row.isActive)">{{ row.isActive ? '停用' : '启用' }}</el-button></template></el-table-column>
  </el-table>
</template>

<style scoped>
.secondary { margin-top: 4px; color: #94a3b8; font-size: 12px; }
.role-list { display: flex; flex-wrap: wrap; gap: 6px; }
</style>
