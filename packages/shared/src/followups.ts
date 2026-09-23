import type { PageQuery } from './api';
import type { FollowUpType } from './enums';

export interface FollowUp {
  id: string;
  leadId: string;
  type: FollowUpType;
  occurredAt: string;
  summary: string;
  result: string | null;
  nextStepAt: string | null;
  createdBy: { id: string; name: string };
  createdAt: string;
  updatedAt: string;
  deletedAt?: string | null;
}

export interface FollowUpQuery extends PageQuery {
  type?: FollowUpType;
  from?: string;
  to?: string;
}

export interface CreateFollowUpInput {
  type: FollowUpType;
  occurredAt: string;
  summary: string;
  result?: string | null;
  nextStepAt?: string | null;
}

export interface UpdateFollowUpInput {
  type?: FollowUpType;
  occurredAt?: string;
  summary?: string;
  result?: string | null;
  nextStepAt?: string | null;
}
