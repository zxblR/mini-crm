<script setup lang="ts">
import { computed } from 'vue';
import type { EChartsCoreOption } from 'echarts/core';
import type { TrendPointDTO } from '@mini-crm/shared';

import BaseEChart from '@/components/charts/BaseEChart.vue';

const props = defineProps<{ items: TrendPointDTO[] }>();
const option = computed<EChartsCoreOption>(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['新增线索', '赢单'] },
  grid: { left: 44, right: 20, top: 46, bottom: 44 },
  xAxis: { type: 'category', data: props.items.map((item) => item.periodStart.slice(0, 10)) },
  yAxis: { type: 'value', minInterval: 1 },
  series: [
    { name: '新增线索', type: 'line', smooth: true, data: props.items.map((item) => item.leadCount), itemStyle: { color: '#2563eb' } },
    { name: '赢单', type: 'line', smooth: true, data: props.items.map((item) => item.wonCount), itemStyle: { color: '#16a34a' } },
  ],
}));
</script>

<template><BaseEChart :option="option" /></template>
