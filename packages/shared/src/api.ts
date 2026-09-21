export const API_PREFIX = '/api/v1';
export const HEALTH_ENDPOINT = `${API_PREFIX}/health`;

export interface ApiMeta {
  requestId?: string;
  page?: number;
  pageSize?: number;
  total?: number;
  from?: string;
  to?: string;
  timezone?: string;
}

export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}

export interface ApiEnvelope<T> {
  data: T;
  meta: ApiMeta;
  error: null;
}

export interface ApiErrorEnvelope {
  data: null;
  meta: ApiMeta;
  error: ApiError;
}

export interface PageQuery {
  page?: number;
  pageSize?: number;
}

export interface PagedResult<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
}

export interface HealthStatus {
  status: 'ok' | 'unavailable';
  service: 'api';
  timestamp: string;
  dependencies?: {
    database: 'up' | 'down';
    redis: 'up' | 'down';
  };
}

export interface ApiHealthResponse {
  status: 'ok';
  service: 'api';
}

export const LEAD_DUPLICATE = 'LEAD_DUPLICATE' as const;
export const STAGE_INVALID_TRANSITION = 'STAGE_INVALID_TRANSITION' as const;

export const ERROR_CODES = {
  validationFailed: 'VALIDATION_FAILED',
  invalidCredentials: 'AUTH_INVALID_CREDENTIALS',
  tokenExpired: 'AUTH_TOKEN_EXPIRED',
  tokenRevoked: 'AUTH_TOKEN_REVOKED',
  forbidden: 'FORBIDDEN',
  notFound: 'RESOURCE_NOT_FOUND',
  userDuplicate: 'USER_DUPLICATE',
  lastAdminRequired: 'LAST_ADMIN_REQUIRED',
  leadDuplicate: LEAD_DUPLICATE,
  stageConflict: 'STAGE_CONFLICT',
  invalidTransition: STAGE_INVALID_TRANSITION,
  lostReasonRequired: 'LOST_REASON_REQUIRED',
  outcomeNoteRequired: 'OUTCOME_NOTE_REQUIRED',
  terminalTask: 'TASK_TERMINAL_STATE',
  taskCompleted: 'TASK_ALREADY_COMPLETED',
  taskCancelled: 'TASK_ALREADY_CANCELLED',
  internal: 'INTERNAL_ERROR',
  unauthorized: 'UNAUTHORIZED',
  userInactive: 'USER_INACTIVE',
  teamNotFound: 'TEAM_NOT_FOUND',
} as const;

export type ErrorCode = (typeof ERROR_CODES)[keyof typeof ERROR_CODES];
