import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import type {
  LoginInput,
  LoginResult,
  RoleCode,
  SessionUser,
} from '@mini-crm/shared';

import {
  loginRequest,
  logoutRequest,
  meRequest,
  refreshRequest,
} from '@/api/auth';

const STORAGE_KEY = 'mini-crm.auth';

interface PersistedSession {
  accessToken: string;
  refreshToken: string | null;
  expiresAt: number | null;
  user: SessionUser | null;
}

function readSession(): PersistedSession | null {
  if (typeof window === 'undefined') return null;

  try {
    const value = window.sessionStorage.getItem(STORAGE_KEY);
    return value ? (JSON.parse(value) as PersistedSession) : null;
  } catch {
    return null;
  }
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null);
  const refreshToken = ref<string | null>(null);
  const expiresAt = ref<number | null>(null);
  const user = ref<SessionUser | null>(null);
  const isHydrated = ref(false);
  const isLoading = ref(false);
  const errorMessage = ref<string | null>(null);

  const isAuthenticated = computed(() => Boolean(accessToken.value && user.value));
  const roles = computed(() => user.value?.roles ?? []);

  function persistSession(): void {
    if (typeof window === 'undefined') return;

    if (!accessToken.value) {
      window.sessionStorage.removeItem(STORAGE_KEY);
      return;
    }

    const session: PersistedSession = {
      accessToken: accessToken.value,
      refreshToken: refreshToken.value,
      expiresAt: expiresAt.value,
      user: user.value,
    };
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  }

  function setSession(result: LoginResult): void {
    accessToken.value = result.accessToken;
    refreshToken.value = result.refreshToken ?? null;
    expiresAt.value = result.expiresIn ? Date.now() + result.expiresIn * 1000 : null;
    user.value = result.user;
    persistSession();
  }

  function clearSession(): void {
    accessToken.value = null;
    refreshToken.value = null;
    expiresAt.value = null;
    user.value = null;
    errorMessage.value = null;
    persistSession();
  }

  function hasRole(role: RoleCode): boolean {
    return roles.value.includes(role);
  }

  function hasAnyRole(requiredRoles: RoleCode[]): boolean {
    if (requiredRoles.length === 0 || roles.value.length === 0) return false;
    return requiredRoles.some((role) => hasRole(role));
  }

  function can(permission: string): boolean {
    return user.value?.permissions.includes(permission) ?? false;
  }

  async function login(input: LoginInput): Promise<void> {
    isLoading.value = true;
    errorMessage.value = null;

    try {
      const result = await loginRequest(input);
      setSession(result);
      user.value = await meRequest();
      persistSession();
    } catch (error) {
      clearSession();
      errorMessage.value = error instanceof Error ? error.message : '登录失败';
      throw error;
    } finally {
      isLoading.value = false;
    }
  }

  async function fetchMe(): Promise<SessionUser> {
    const currentUser = await meRequest();
    user.value = currentUser;
    persistSession();
    return currentUser;
  }

  async function refreshSession(): Promise<void> {
    if (!refreshToken.value) {
      throw new Error('缺少 refresh token');
    }

    const result = await refreshRequest({ refreshToken: refreshToken.value });
    accessToken.value = result.accessToken;
    refreshToken.value = result.refreshToken ?? refreshToken.value;
    expiresAt.value = result.expiresIn ? Date.now() + result.expiresIn * 1000 : null;
    if (result.user) user.value = result.user;
    persistSession();
  }

  async function hydrate(): Promise<void> {
    if (isHydrated.value) return;

    const persisted = readSession();
    if (!persisted) {
      isHydrated.value = true;
      return;
    }

    accessToken.value = persisted.accessToken;
    refreshToken.value = persisted.refreshToken;
    expiresAt.value = persisted.expiresAt;
    user.value = persisted.user;

    try {
      if (expiresAt.value !== null && expiresAt.value <= Date.now()) {
        await refreshSession();
      }
      await fetchMe();
    } catch {
      clearSession();
    } finally {
      isHydrated.value = true;
    }
  }

  async function logout(): Promise<void> {
    const token = refreshToken.value;
    try {
      if (token) await logoutRequest({ refreshToken: token });
    } finally {
      clearSession();
    }
  }

  return {
    accessToken,
    refreshToken,
    expiresAt,
    user,
    isHydrated,
    isLoading,
    errorMessage,
    isAuthenticated,
    roles,
    hasRole,
    hasAnyRole,
    can,
    login,
    fetchMe,
    refreshSession,
    hydrate,
    logout,
    clearSession,
  };
});
