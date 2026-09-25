<script setup lang="ts">
import { computed } from 'vue';
import type { EChartsCoreOption } from 'echarts/core';
import type { ResponseTimeStatsDTO } from '@mini-crm/shared';

import BaseEChart from '@/components/charts/BaseEChart.vue';

const props = defineProps<{ data: ResponseTimeStatsDTO }>();
const option = computed<EChartsCoreOption>(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 44, right: 20, top: 30, bottom: 44 },
  xAxis: { type: 'category', data: props.data.buckets.map((bucket) => bucket.name) },
  yAxis: { type: 'value', minInterval: 1 },
  series: [{ type: 'bar', data: props.data.buckets.map((bucket) => bucket.count), itemStyle: { color: '#0891b2' } }],
}));
</script>

<template><BaseEChart :option="option" /></template>
