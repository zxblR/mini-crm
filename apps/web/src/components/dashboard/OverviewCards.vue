<script setup lang="ts">
import type { StatsOverviewDTO } from '@mini-crm/shared';
import { LeadStatus } from '@mini-crm/shared';

const props = defineProps<{ overview: StatsOverviewDTO }>();

function wonCount(): number {
  return props.overview.statusCounts.find((item) => item.status === LeadStatus.Won)?.count ?? 0;
}

function conversionRate(): string {
  return new Intl.NumberFormat('zh-CN', { style: 'percent', maximumFractionDigits: 1 }).format(
    props.overview.conversionRate,
  );
}
</script>

<template>
  <div class="dashboard-cards">
    <el-card shadow="never"><span>总线索数</span><strong>{{ overview.totalLeads }}</strong></el-card>
    <el-card shadow="never"><span>本周期新增</span><strong>{{ overview.newLeads }}</strong></el-card>
    <el-card shadow="never"><span>成交数</span><strong>{{ wonCount() }}</strong></el-card>
    <el-card shadow="never"><span>转化率</span><strong>{{ conversionRate() }}</strong></el-card>
  </div>
</template>

<style scoped>
.dashboard-cards { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
.dashboard-cards :deep(.el-card__body) { display: grid; gap: 10px; }
.dashboard-cards span { color: #64748b; font-size: 13px; }
.dashboard-cards strong { color: #0f172a; font-size: 28px; }
@media (max-width: 900px) { .dashboard-cards { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
