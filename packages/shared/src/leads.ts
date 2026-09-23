import type { PageQuery } from './api';
import type {
  ImportJobStatus,
  LeadStatus,
  SortOrder,
  TimelineEventType,
} from './enums';

export type OpenLeadStatus = Exclude<LeadStatus, LeadStatus.Won | LeadStatus.Lost>;

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
  ownerId: string | null;
  owner: LeadOwner | null;
  status: LeadStatus;
  tags: Tag[];
  nextFollowUpAt: string | null;
  archivedAt: string | null;
  closedAt: string | null;
  outcomeNote: string | null;
  lostReason: string | null;
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
  deleted?: boolean;
  metadata?: Record<string, unknown>;
}

export interface LeadQuery extends PageQuery {
  keyword?: string;
  status?: LeadStatus;
  ownerId?: string;
  source?: string;
  from?: string;
  to?: string;
  archived?: boolean;
  sortBy?: 'createdAt' | 'updatedAt' | 'nextFollowUpAt' | 'company';
  sortOrder?: SortOrder;
}

export type LeadExportQuery = Omit<LeadQuery, 'page' | 'pageSize'>;

export interface CreateLeadRequest {
  name?: string | null;
  company?: string | null;
  phone?: string | null;
  email?: string | null;
  source: string;
  industry?: string | null;
  region?: string | null;
  notes?: string | null;
  status?: OpenLeadStatus;
  ownerId?: string | null;
  tagIds?: string[];
  nextFollowUpAt?: string | null;
}

export interface UpdateLeadRequest {
  name?: string | null;
  company?: string | null;
  phone?: string | null;
  email?: string | null;
  source?: string;
  industry?: string | null;
  region?: string | null;
  notes?: string | null;
  ownerId?: string | null;
  tagIds?: string[];
  nextFollowUpAt?: string | null;
}

export interface ChangeLeadStatusRequest {
  status?: LeadStatus;
  note?: string | null;
  outcomeNote?: string | null;
  lostReason?: string | null;
}

export interface LeadStatusHistory {
  id: string;
  fromStatus: LeadStatus | null;
  toStatus: LeadStatus;
  note: string | null;
  outcomeNote: string | null;
  lostReason: string | null;
  actor: LeadOwner;
  createdAt: string;
}

export interface ChangeLeadStatusResponse {
  lead: LeadSummary;
  history: LeadStatusHistory;
}

export interface AssignLeadRequest {
  ownerId: string;
}

export interface BatchAssignLeadsRequest {
  leadIds: string[];
  ownerId: string;
}

export interface BatchAssignLeadsResponse {
  updated: number;
  leadIds: string[];
}

export interface LeadArchiveResponse {
  id: string;
  archivedAt: string | null;
}

export interface LeadImportAccepted {
  jobId: string;
  status: ImportJobStatus.Queued;
}

export interface LeadImportJob {
  id: string;
  type: 'lead_import';
  status: ImportJobStatus;
  processed: number;
  succeeded: number;
  failed: number;
  errorFileUrl: string | null;
  createdAt: string;
  finishedAt: string | null;
}

export type CreateLeadInput = CreateLeadRequest;
export type UpdateLeadInput = UpdateLeadRequest;
export type ChangeStageInput = ChangeLeadStatusRequest;
export type StageHistory = LeadStatusHistory;
