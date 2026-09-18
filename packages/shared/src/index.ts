export const API_PREFIX = '/api';

export const HEALTH_ENDPOINT = `${API_PREFIX}/health`;

export type ApiHealthResponse = {
  status: 'ok';
  service: 'api';
};

export type ApiEnvelope<T> = {
  data: T;
  meta: {
    requestId?: string;
  };
  error: null;
};
