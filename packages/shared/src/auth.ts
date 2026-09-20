import type { RoleCode } from './enums';

export interface LoginInput {
  account: string;
  password: string;
}

export interface RefreshInput {
  refreshToken: string;
}

export interface SessionUser {
  id: string;
  organizationId: string;
  name: string;
  email: string | null;
  phone: string | null;
  roles: RoleCode[];
  permissions: string[];
}

export interface LoginResult {
  accessToken: string;
  refreshToken?: string;
  expiresIn: number;
  user: SessionUser;
}
