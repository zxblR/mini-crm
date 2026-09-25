<script setup lang="ts">
import type { LeadDetail } from '@mini-crm/shared';

import LeadStatusTag from './LeadStatusTag.vue';
import LeadTagList from './LeadTagList.vue';
import { formatDateTime, leadDisplayName } from '@/utils/format';

defineProps<{ lead: LeadDetail }>();
</script>

<template>
  <el-card shadow="never">
    <template #header><div class="card-header"><strong>{{ leadDisplayName(lead.name, lead.company) }}</strong><LeadStatusTag :status="lead.status" /></div></template>
    <el-descriptions :column="2" border>
      <el-descriptions-item label="公司">{{ lead.company || '—' }}</el-descriptions-item>
      <el-descriptions-item label="负责人">{{ lead.owner?.name ?? '未分配' }}</el-descriptions-item>
      <el-descriptions-item label="手机">{{ lead.phone || '—' }}</el-descriptions-item>
      <el-descriptions-item label="邮箱">{{ lead.email || '—' }}</el-descriptions-item>
      <el-descriptions-item label="来源">{{ lead.source }}</el-descriptions-item>
      <el-descriptions-item label="行业">{{ lead.industry || '—' }}</el-descriptions-item>
      <el-descriptions-item label="地区">{{ lead.region || '—' }}</el-descriptions-item>
      <el-descriptions-item label="下次跟进">{{ formatDateTime(lead.nextFollowUpAt) }}</el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ formatDateTime(lead.createdAt) }}</el-descriptions-item>
      <el-descriptions-item label="最近更新">{{ formatDateTime(lead.updatedAt) }}</el-descriptions-item>
      <el-descriptions-item label="标签" :span="2"><LeadTagList :tags="lead.tags" /></el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ lead.notes || '—' }}</el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
</style>
