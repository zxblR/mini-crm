<script setup lang="ts">
import { computed } from 'vue';
import type { EChartsCoreOption } from 'echarts/core';
import type { SalesRankingDTO } from '@mini-crm/shared';

import BaseEChart from '@/components/charts/BaseEChart.vue';

const props = defineProps<{ items: SalesRankingDTO[] }>();
const rows = computed(() => [...props.items].reverse());
const option = computed<EChartsCoreOption>(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['跟进数', '赢单数'] },
  grid: { left: 90, right: 20, top: 46, bottom: 34 },
  xAxis: { type: 'value', minInterval: 1 },
  yAxis: { type: 'category', data: rows.value.map((item) => item.ownerName) },
  series: [
    { name: '跟进数', type: 'bar', data: rows.value.map((item) => item.followUpCount), itemStyle: { color: '#7c3aed' } },
    { name: '赢单数', type: 'bar', data: rows.value.map((item) => item.wonCount), itemStyle: { color: '#16a34a' } },
  ],
}));
</script>

<template><BaseEChart :option="option" /></template>
