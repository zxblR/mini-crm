<script setup lang="ts">
import type { LeadSummary } from '@mini-crm/shared';

import LeadStatusTag from './LeadStatusTag.vue';
import LeadTagList from './LeadTagList.vue';
import { formatDateTime, leadDisplayName } from '@/utils/format';
import { usePermissions } from '@/composables/usePermissions';

defineProps<{ items: LeadSummary[]; loading: boolean }>();
const emit = defineEmits<{
  edit: [lead: LeadSummary];
  status: [lead: LeadSummary];
  archive: [lead: LeadSummary, archived: boolean];
}>();
const permissions = usePermissions();
</script>

<template>
  <el-table v-loading="loading" :data="items" row-key="id">
    <el-table-column label="线索" min-width="180">
      <template #default="{ row }">
        <router-link class="lead-link" :to="'/leads/' + row.id">{{ leadDisplayName(row.name, row.company) }}</router-link>
        <div class="secondary">{{ row.company || row.email || row.phone || '暂无联系信息' }}</div>
      </template>
    </el-table-column>
    <el-table-column label="状态" width="110"><template #default="{ row }"><LeadStatusTag :status="row.status" /></template></el-table-column>
    <el-table-column label="负责人" width="130"><template #default="{ row }">{{ row.owner?.name ?? '未分配' }}</template></el-table-column>
    <el-table-column label="来源" prop="source" width="120" />
    <el-table-column label="标签" min-width="160"><template #default="{ row }"><LeadTagList :tags="row.tags" /></template></el-table-column>
    <el-table-column label="下次跟进" width="170"><template #default="{ row }">{{ formatDateTime(row.nextFollowUpAt) }}</template></el-table-column>
    <el-table-column label="最近更新" width="170"><template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template></el-table-column>
    <el-table-column label="操作" fixed="right" width="210">
      <template #default="{ row }">
        <el-button link type="primary" @click="$router.push('/leads/' + row.id)">详情</el-button>
        <el-button v-if="permissions.canEditLead(row)" link type="primary" @click="emit('edit', row)">编辑</el-button>
        <el-button v-if="permissions.canChangeLeadStatus(row)" link type="primary" @click="emit('status', row)">推进</el-button>
        <el-button v-if="!row.archivedAt && permissions.canArchiveLead(row)" link type="danger" @click="emit('archive', row, true)">归档</el-button>
        <el-button v-else-if="row.archivedAt && permissions.canRestoreLead()" link type="primary" @click="emit('archive', row, false)">恢复</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.lead-link { color: #2563eb; font-weight: 600; text-decoration: none; }
.secondary { margin-top: 4px; color: #94a3b8; font-size: 12px; }
</style>
