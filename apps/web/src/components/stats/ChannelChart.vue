<script setup lang="ts">
import { computed } from 'vue';
import type { EChartsCoreOption } from 'echarts/core';
import type { ChannelStatsDTO } from '@mini-crm/shared';

import BaseEChart from '@/components/charts/BaseEChart.vue';

const props = defineProps<{ items: ChannelStatsDTO[] }>();
const option = computed<EChartsCoreOption>(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['线索数', '赢单数'] },
  grid: { left: 44, right: 20, top: 46, bottom: 58 },
  xAxis: { type: 'category', data: props.items.map((item) => item.source), axisLabel: { rotate: 25 } },
  yAxis: { type: 'value', minInterval: 1 },
  series: [
    { name: '线索数', type: 'bar', data: props.items.map((item) => item.leadCount), itemStyle: { color: '#2563eb' } },
    { name: '赢单数', type: 'bar', data: props.items.map((item) => item.wonCount), itemStyle: { color: '#16a34a' } },
  ],
}));
</script>

<template><BaseEChart :option="option" /></template>
