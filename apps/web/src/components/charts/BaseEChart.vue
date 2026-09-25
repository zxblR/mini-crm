<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { EChartsCoreOption } from 'echarts/core';

import { echarts } from '@/charts/echarts';

const props = defineProps<{
  option: EChartsCoreOption;
  height?: string;
}>();

const container = ref<HTMLDivElement>();
let chart: ReturnType<typeof echarts.init> | null = null;
let resizeObserver: ResizeObserver | null = null;

function render(): void {
  if (!container.value) return;
  chart ??= echarts.init(container.value);
  chart.setOption(props.option, { notMerge: true });
}

onMounted(async () => {
  await nextTick();
  render();
  if (container.value) {
    resizeObserver = new ResizeObserver(() => chart?.resize());
    resizeObserver.observe(container.value);
  }
});

watch(
  () => props.option,
  () => render(),
  { deep: true },
);

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  chart?.dispose();
  chart = null;
});
</script>

<template>
  <div ref="container" class="base-chart" :style="{ height: height ?? '320px' }" />
</template>

<style scoped>
.base-chart { width: 100%; min-width: 0; }
</style>
