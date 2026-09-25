import type {
  ApiEnvelope,
  ChannelStatsDTO,
  Granularity,
  LossReasonStatsDTO,
  ResponseTimeStatsDTO,
  SalesRankingDTO,
  StatsOverviewDTO,
  TrendPointDTO,
} from '@mini-crm/shared';

import { http } from './http';
import {
  compactParams,
  type PagedData,
  unwrapPagedResponse,
  unwrapResponse,
} from './response';

export interface StatsRequestQuery {
  from?: string;
  to?: string;
  ownerId?: string;
}

export async function getStatsOverview(query: StatsRequestQuery): Promise<StatsOverviewDTO> {
  const response = await http.get<ApiEnvelope<StatsOverviewDTO>>('/dashboard/overview', {
    params: compactParams(query),
  });
  return unwrapResponse(response);
}

export async function getChannelStats(
  query: StatsRequestQuery & { page?: number; pageSize?: number },
): Promise<PagedData<ChannelStatsDTO>> {
  const response = await http.get<ApiEnvelope<ChannelStatsDTO[]>>('/dashboard/channels', {
    params: compactParams(query),
  });
  return unwrapPagedResponse(response);
}

export async function getSalesRanking(
  query: StatsRequestQuery & { page?: number; pageSize?: number },
): Promise<PagedData<SalesRankingDTO>> {
  const response = await http.get<ApiEnvelope<SalesRankingDTO[]>>('/dashboard/sales-ranking', {
    params: compactParams(query),
  });
  return unwrapPagedResponse(response);
}

export async function getTrendStats(
  query: StatsRequestQuery & { granularity: Granularity },
): Promise<TrendPointDTO[]> {
  const response = await http.get<ApiEnvelope<TrendPointDTO[]>>('/dashboard/trend', {
    params: compactParams(query),
  });
  return unwrapResponse(response);
}

export async function getLossReasonStats(
  query: StatsRequestQuery & { page?: number; pageSize?: number },
): Promise<PagedData<LossReasonStatsDTO>> {
  const response = await http.get<ApiEnvelope<LossReasonStatsDTO[]>>('/dashboard/loss-reasons', {
    params: compactParams(query),
  });
  return unwrapPagedResponse(response);
}

export async function getResponseTimeStats(
  query: StatsRequestQuery,
): Promise<ResponseTimeStatsDTO> {
  const response = await http.get<ApiEnvelope<ResponseTimeStatsDTO>>('/dashboard/response-times', {
    params: compactParams(query),
  });
  return unwrapResponse(response);
}
