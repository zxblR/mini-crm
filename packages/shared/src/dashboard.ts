import type { PageQuery } from './api';

export interface DashboardFilter {
  from?: string;
  to?: string;
  ownerId?: string;
  source?: string;
}

export interface DashboardSummary {
  totalLeads: number;
  openLeads: number;
  wonLeads: number;
  lostLeads: number;
  overdueTasks: number;
  conversionRate: number;
  newThisPeriod: number;
  activitiesThisPeriod: number;
}

export interface FunnelDatum {
  stageId: string;
  stageName: string;
  color: string;
  count: number;
  percentage: number;
}

export interface SourceDatum {
  source: string;
  count: number;
  won: number;
  conversionRate: number;
}

export interface OwnerDatum {
  ownerId: string;
  ownerName: string;
  total: number;
  won: number;
  overdueTasks: number;
  conversionRate: number;
}

export interface ActivityItem {
  id: string;
  type: string;
  leadId: string;
  leadName: string;
  title: string;
  actorName: string | null;
  occurredAt: string;
}

export type DashboardListQuery = DashboardFilter & PageQuery;

export enum Granularity {
  Day = 'day',
  Week = 'week',
  Month = 'month',
}

export interface StatsOverviewDTO {
  totalLeads: number;
  newLeads: number;
  statusCounts: Array<{ status: string; count: number }>;
  conversionRate: number;
  avgDealCycleSeconds: number | null;
  respondedCount: number;
  unrespondedCount: number;
  avgResponseSeconds: number | null;
}

export interface ChannelStatsDTO {
  source: string;
  leadCount: number;
  wonCount: number;
  conversionRate: number;
}

export interface SalesRankingDTO {
  ownerId: string;
  ownerName: string;
  followUpCount: number;
  wonCount: number;
  avgDealCycleSeconds: number | null;
  conversionRate: number;
}

export interface TrendPointDTO {
  periodStart: string;
  leadCount: number;
  wonCount: number;
}

export interface ResponseTimeStatsDTO {
  respondedCount: number;
  unrespondedCount: number;
  avgResponseSeconds: number | null;
  buckets: Array<{ name: '<1h' | '1h-24h' | '1d-7d' | '>7d' | 'unresponded'; count: number }>;
}

export interface LossReasonStatsDTO {
  lostReason: string;
  count: number;
}
