import type { PageQuery } from './api';
import type { TaskStatus } from './enums';

export interface TaskItem {
  id: string;
  leadId: string;
  leadName: string;
  title: string;
  dueAt: string;
  status: Exclude<TaskStatus, TaskStatus.Overdue>;
  isOverdue: boolean;
  assignee: { id: string; name: string };
  resolutionNote: string | null;
  completedAt: string | null;
  cancelledAt: string | null;
  createdAt: string;
}

export interface TaskQuery extends PageQuery {
  status?: TaskStatus;
  dueFrom?: string;
  dueTo?: string;
  assigneeId?: string;
  leadId?: string;
}

export type TodayTaskQuery = PageQuery;

export interface UpdateTaskInput {
  title?: string;
  dueAt?: string;
  assigneeId?: string;
}

export interface ResolveTaskInput {
  note?: string | null;
}
