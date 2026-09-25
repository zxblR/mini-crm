<script setup lang="ts">
import type { LeadSummary } from '@mini-crm/shared';

import LeadStatusTag from '@/components/leads/LeadStatusTag.vue';
import { formatDateTime, leadDisplayName } from '@/utils/format';

defineProps<{ items: LeadSummary[] }>();
</script>

<template>
  <el-card shadow="never">
    <template #header><div class="panel-header"><strong>最近更新线索</strong><router-link to="/leads">查看线索列表</router-link></div></template>
    <el-empty v-if="items.length === 0" description="暂无最近线索" :image-size="72" />
    <div v-else class="panel-list">
      <router-link v-for="lead in items" :key="lead.id" :to="'/leads/' + lead.id">
        <div><strong>{{ leadDisplayName(lead.name, lead.company) }}</strong><span>{{ lead.owner?.name ?? '未分配' }} · {{ formatDateTime(lead.updatedAt) }}</span></div>
        <LeadStatusTag :status="lead.status" />
      </router-link>
    </div>
  </el-card>
</template>

<style scoped>
.panel-header, .panel-list a { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.panel-header a { color: #2563eb; font-size: 13px; text-decoration: none; }
.panel-list { display: grid; gap: 12px; }
.panel-list a { padding-bottom: 12px; color: #0f172a; border-bottom: 1px solid #e2e8f0; text-decoration: none; }
.panel-list a:last-child { padding-bottom: 0; border-bottom: 0; }
.panel-list div { display: grid; gap: 4px; }
.panel-list span { color: #64748b; font-size: 12px; }
</style>
