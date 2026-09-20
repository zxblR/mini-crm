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
