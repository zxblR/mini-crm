export enum RoleCode {
  Owner = 'OWNER',
  Admin = 'ADMIN',
  Sales = 'SALES',
  Support = 'SUPPORT',
}

export enum LeadStatus {
  New = 'new',
  Contacted = 'contacted',
  Qualified = 'qualified',
  Proposal = 'proposal',
  Negotiation = 'negotiation',
  Won = 'won',
  Lost = 'lost',
}

export enum FollowUpType {
  Call = 'call',
  Wechat = 'wechat',
  Email = 'email',
  Meeting = 'meeting',
  Other = 'other',
}

export enum TaskStatus {
  Pending = 'pending',
  Completed = 'completed',
  Cancelled = 'cancelled',
  Overdue = 'overdue',
}

export enum TimelineEventType {
  Created = 'created',
  Updated = 'updated',
  Assigned = 'assigned',
  StageChanged = 'stage_changed',
  FollowUp = 'follow_up',
  TaskCreated = 'task_created',
  TaskCompleted = 'task_completed',
  TaskCancelled = 'task_cancelled',
  TaskUpdated = 'task_updated',
  Archived = 'archived',
  Restored = 'restored',
}

export enum ImportJobStatus {
  Queued = 'queued',
  Running = 'running',
  Completed = 'completed',
  Failed = 'failed',
}

export enum SortOrder {
  Asc = 'asc',
  Desc = 'desc',
}

export const ROLE_LABELS: Record<RoleCode, string> = {
  [RoleCode.Owner]: '所有者',
  [RoleCode.Admin]: '管理员',
  [RoleCode.Sales]: '销售',
  [RoleCode.Support]: '支持成员',
};

export const FOLLOW_UP_TYPE_LABELS: Record<FollowUpType, string> = {
  [FollowUpType.Call]: '电话',
  [FollowUpType.Wechat]: '微信',
  [FollowUpType.Email]: '邮件',
  [FollowUpType.Meeting]: '会议',
  [FollowUpType.Other]: '其他',
};
