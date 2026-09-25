import { ref } from 'vue';
import { defineStore } from 'pinia';
import type {
  ChannelStatsDTO,
  LossReasonStatsDTO,
  ResponseTimeStatsDTO,
  SalesRankingDTO,
  StatsOverviewDTO,
  TrendPointDTO,
} from '@mini-crm/shared';
import { Granularity } from '@mini-crm/shared';

import {
  getChannelStats,
  getLossReasonStats,
  getResponseTimeStats,
  getSalesRanking,
  getStatsOverview,
  getTrendStats,
  type StatsRequestQuery,
} from '@/api/dashboard';
import { normalizeApiError } from '@/api/http';

export interface StatsQuery extends StatsRequestQuery {
  granularity: Granularity;
}

function defaultRange(): StatsQuery {
  const to = new Date();
  const from = new Date(to.getTime() - 30 * 24 * 60 * 60 * 1000);
  return { from: from.toISOString(), to: to.toISOString(), granularity: Granularity.Day };
}

export const useStatsStore = defineStore('stats', () => {
  const query = ref<StatsQuery>(defaultRange());
  const overview = ref<StatsOverviewDTO | null>(null);
  const channels = ref<ChannelStatsDTO[]>([]);
  const salesRanking = ref<SalesRankingDTO[]>([]);
  const trend = ref<TrendPointDTO[]>([]);
  const lossReasons = ref<LossReasonStatsDTO[]>([]);
  const responseTimes = ref<ResponseTimeStatsDTO | null>(null);
  const loading = ref(false);
  const errorMessage = ref<string | null>(null);

  async function fetchAll(overrides: Partial<StatsQuery> = {}): Promise<void> {
    query.value = { ...query.value, ...overrides };
    loading.value = true;
    errorMessage.value = null;
    const common = {
      from: query.value.from,
      to: query.value.to,
      ownerId: query.value.ownerId,
    };
    try {
      const [overviewResult, channelResult, rankingResult, trendResult, lossResult, responseResult] =
        await Promise.all([
          getStatsOverview(common),
          getChannelStats({ ...common, page: 1, pageSize: 100 }),
          getSalesRanking({ ...common, page: 1, pageSize: 100 }),
          getTrendStats({ ...common, granularity: query.value.granularity }),
          getLossReasonStats({ ...common, page: 1, pageSize: 100 }),
          getResponseTimeStats(common),
        ]);
      overview.value = overviewResult;
      channels.value = channelResult.items;
      salesRanking.value = rankingResult.items;
      trend.value = trendResult;
      lossReasons.value = lossResult.items;
      responseTimes.value = responseResult;
    } catch (error) {
      errorMessage.value = normalizeApiError(error).message;
      throw error;
    } finally {
      loading.value = false;
    }
  }

  return {
    query,
    overview,
    channels,
    salesRanking,
    trend,
    lossReasons,
    responseTimes,
    loading,
    errorMessage,
    fetchAll,
  };
});
