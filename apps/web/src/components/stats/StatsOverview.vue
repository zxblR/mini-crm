<script setup lang="ts">
import type { StatsOverviewDTO } from '@mini-crm/shared';

defineProps<{ overview: StatsOverviewDTO }>();

function percent(value: number): string {
  return new Intl.NumberFormat('zh-CN', { style: 'percent', maximumFractionDigits: 1 }).format(value);
}

function duration(seconds: number | null): string {
  if (seconds === null) return '—';
  const days = seconds / 86400;
  return days >= 1 ? days.toFixed(1) + ' 天' : (seconds / 3600).toFixed(1) + ' 小时';
}
</script>

<template>
  <div class="overview-grid">
    <el-card shadow="never"><span>线索总量</span><strong>{{ overview.totalLeads }}</strong></el-card>
    <el-card shadow="never"><span>本周期新增</span><strong>{{ overview.newLeads }}</strong></el-card>
    <el-card shadow="never"><span>转化率</span><strong>{{ percent(overview.conversionRate) }}</strong></el-card>
    <el-card shadow="never"><span>平均成交周期</span><strong>{{ duration(overview.avgDealCycleSeconds) }}</strong></el-card>
    <el-card shadow="never"><span>已响应线索</span><strong>{{ overview.respondedCount }}</strong></el-card>
    <el-card shadow="never"><span>未响应线索</span><strong>{{ overview.unrespondedCount }}</strong></el-card>
  </div>
</template>

<style scoped>
.overview-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin-bottom: 20px; }
.overview-grid :deep(.el-card__body) { display: grid; gap: 10px; }
.overview-grid span { color: #64748b; font-size: 13px; }
.overview-grid strong { color: #0f172a; font-size: 26px; }
@media (max-width: 800px) { .overview-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
