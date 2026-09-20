import type { PageQuery } from './api';
import type { SortOrder, TimelineEventType } from './enums';

export interface PipelineStage {
  id: string;
  code: string;
  name: string;
  color: string;
  sortOrder: number;
  isDefault: boolean;
  isWon: boolean;
  isLost: boolean;
  isActive: boolean;
}

export interface Tag {
  id: string;
  name: string;
  color: string;
}

export interface LeadOwner {
  id: string;
  name: string;
  email: string | null;
}

export interface LeadSummary {
  id: string;
  name: string | null;
  company: string | null;
  phone: string | null;
  email: string | null;
  source: string;
  industry: string | null;
  region: string | null;
  owner: LeadOwner | null;
  stage: PipelineStage;
  tags: Tag[];
  nextFollowUpAt: string | null;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface LeadDetail extends LeadSummary {
  notes: string | null;
  createdBy: LeadOwner;
  timeline: TimelineEvent[];
}

export interface TimelineEvent {
  id: string;
  type: TimelineEventType;
  title: string;
  description: string | null;
  actorName: string | null;
  occurredAt: string;
  metadata?: Record<string, unknown>;
}

export interface LeadQuery extends PageQuery {
  keyword?: string;
  stageId?: string;
  ownerId?: string;
  source?: string;
  from?: string;
  to?: string;
  archived?: boolean;
  sortBy?: 'createdAt' | 'updatedAt' | 'nextFollowUpAt' | 'company';
  sortOrder?: SortOrder;
}

export interface CreateLeadInput {
  name?: string | null;
  company?: string | null;
  phone?: string | null;
  email?: string | null;
  source: string;
  industry?: string | null;
  region?: string | null;
  notes?: string | null;
  stageId?: string;
  ownerId?: string | null;
  tagIds?: string[];
  nextFollowUpAt?: string | null;
}

export type UpdateLeadInput = Partial<Omit<CreateLeadInput, 'stageId'>>;

export interface ChangeStageInput {
  stageId: string;
  note?: string | null;
  outcomeNote?: string | null;
  lostReason?: string | null;
}

export interface StageHistory {
  id: string;
  fromStage: PipelineStage | null;
  toStage: PipelineStage;
  note: string | null;
  outcomeNote: string | null;
  lostReason: string | null;
  actor: LeadOwner;
  createdAt: string;
}
