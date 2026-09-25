<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import AsyncPageState from '@/components/common/AsyncPageState.vue';
import PageHeader from '@/components/common/PageHeader.vue';
import ChannelChart from '@/components/stats/ChannelChart.vue';
import LossReasonTable from '@/components/stats/LossReasonTable.vue';
import ResponseTimeChart from '@/components/stats/ResponseTimeChart.vue';
import SalesRankingChart from '@/components/stats/SalesRankingChart.vue';
import StatsFilterBar from '@/components/stats/StatsFilterBar.vue';
import StatsOverview from '@/components/stats/StatsOverview.vue';
import TrendChart from '@/components/stats/TrendChart.vue';
import { usePermissions } from '@/composables/usePermissions';
import { useDirectoryStore } from '@/stores/directory';
import { useStatsStore, type StatsQuery } from '@/stores/stats';

const store = useStatsStore();
const directory = useDirectoryStore();
const permissions = usePermissions();
const filters = ref<StatsQuery>({ ...store.query });
const empty = computed(() =>
  !store.overview && store.channels.length === 0 && store.trend.length === 0,
);

async function load(query: StatsQuery = filters.value): Promise<void> {
  filters.value = query;
  await store.fetchAll(query).catch(() => undefined);
}

onMounted(async () => {
  if (permissions.isManager.value) await directory.loadOwners().catch(() => undefined);
  await load();
});
</script>

<template>
  <section>
    <PageHeader title="统计" description="查看所选周期内的渠道、销售、趋势和响应效率。" />
    <StatsFilterBar
      v-model="filters"
      :owners="directory.owners"
      :show-owner-filter="permissions.isManager.value"
      @search="load"
    />
    <AsyncPageState
      :loading="store.loading"
      :error-message="store.errorMessage"
      :empty="empty"
      empty-description="当前范围暂无统计数据"
      @retry="load()"
    >
      <StatsOverview v-if="store.overview" :overview="store.overview" />
      <div class="chart-grid">
        <el-card shadow="never"><template #header><strong>渠道表现</strong></template><ChannelChart :items="store.channels" /></el-card>
        <el-card shadow="never"><template #header><strong>销售排名</strong></template><SalesRankingChart :items="store.salesRanking" /></el-card>
        <el-card class="chart-grid__wide" shadow="never"><template #header><strong>线索趋势</strong></template><TrendChart :items="store.trend" /></el-card>
        <el-card shadow="never"><template #header><strong>首次响应时长</strong></template><ResponseTimeChart v-if="store.responseTimes" :data="store.responseTimes" /><el-empty v-else description="暂无响应数据" /></el-card>
        <el-card shadow="never"><template #header><strong>流失原因</strong></template><LossReasonTable :items="store.lossReasons" /></el-card>
      </div>
    </AsyncPageState>
  </section>
</template>

<style scoped>
.chart-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; }
.chart-grid__wide { grid-column: 1 / -1; }
@media (max-width: 900px) { .chart-grid { grid-template-columns: 1fr; } .chart-grid__wide { grid-column: auto; } }
</style>
