import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { ElMessage } from 'element-plus';

import { pinia } from '@/stores/pinia';
import { useAuthStore } from '@/stores/auth';

declare module 'axios' {
  interface AxiosRequestConfig {
    skipErrorToast?: boolean;
    _retry?: boolean;
  }
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  timeout: Number(import.meta.env.VITE_API_TIMEOUT_MS ?? 10000),
});

let refreshPromise: Promise<string | null> | null = null;

function isAuthEndpoint(url: string | undefined): boolean {
  return Boolean(url && /\/auth\/(login|refresh|logout)$/.test(url));
}

function setAuthorization(config: InternalAxiosRequestConfig, token: string): void {
  config.headers.set('Authorization', `Bearer ${token}`);
}

function getStoredAccessToken(): string | null {
  if (typeof window === 'undefined') return null;

  try {
    const raw = window.sessionStorage.getItem('mini-crm.auth');
    if (!raw) return null;
    const session = JSON.parse(raw) as { accessToken?: string };
    return session.accessToken ?? null;
  } catch {
    return null;
  }
}

function getApiErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const responseData = error.response?.data as
      | { error?: { message?: string } }
      | undefined;
    if (responseData?.error?.message) return responseData.error.message;
    if (error.code === 'ECONNABORTED') return '请求超时，请稍后重试';
    if (!error.response) return '网络连接失败，请检查服务是否启动';
    if (error.response.status >= 500) return '服务暂时不可用，请稍后重试';
    if (error.response.status === 403) return '没有权限执行此操作';
  }
  return error instanceof Error ? error.message : '请求失败，请稍后重试';
}

async function refreshAccessToken(): Promise<string | null> {
  const auth = useAuthStore(pinia);
  if (!auth.refreshToken) return null;

  if (!refreshPromise) {
    refreshPromise = auth
      .refreshSession()
      .then(() => auth.accessToken)
      .catch(() => {
        auth.clearSession();
        return null;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }

  return refreshPromise;
}

http.interceptors.request.use((config) => {
  const token = getStoredAccessToken();
  if (token && !isAuthEndpoint(config.url)) setAuthorization(config, token);
  return config;
});

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config;
    const status = error.response?.status;

    if (status === 401 && config && !config._retry && !isAuthEndpoint(config.url)) {
      config._retry = true;
      const token = await refreshAccessToken();

      if (token) {
        setAuthorization(config, token);
        return http(config);
      }

      const auth = useAuthStore(pinia);
      auth.clearSession();
      if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
        const redirect = `${window.location.pathname}${window.location.search}`;
        window.location.assign(`/login?redirect=${encodeURIComponent(redirect)}`);
      }
    }

    if (!config?.skipErrorToast && status !== 401) {
      ElMessage.error(getApiErrorMessage(error));
    }

    return Promise.reject(error);
  },
);

export { getApiErrorMessage };
