import type {
  ApiEnvelope,
  LoginInput,
  LoginResult,
  RefreshInput,
  SessionUser,
} from '@mini-crm/shared';

import { http } from './http';

interface RefreshResult {
  accessToken: string;
  refreshToken?: string;
  expiresIn: number;
  user?: SessionUser;
}

function unwrap<T>(response: { data: ApiEnvelope<T> }): T {
  return response.data.data;
}

export async function loginRequest(input: LoginInput): Promise<LoginResult> {
  const response = await http.post<ApiEnvelope<LoginResult>>('/auth/login', input, {
    skipErrorToast: true,
  });
  return unwrap(response);
}

export async function meRequest(): Promise<SessionUser> {
  const response = await http.get<ApiEnvelope<SessionUser>>('/auth/me', {
    skipErrorToast: true,
  });
  return unwrap(response);
}

export async function refreshRequest(input: RefreshInput): Promise<RefreshResult> {
  const response = await http.post<ApiEnvelope<RefreshResult>>('/auth/refresh', input, {
    skipErrorToast: true,
  });
  return unwrap(response);
}

export async function logoutRequest(input: RefreshInput): Promise<void> {
  await http.post<ApiEnvelope<{ loggedOut: boolean }>>('/auth/logout', input, {
    skipErrorToast: true,
  });
}
