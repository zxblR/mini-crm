import type { PageQuery } from './api';
import type { RoleCode } from './enums';

export interface UserSummary {
  id: string;
  organizationId: string;
  name: string;
  email: string | null;
  phone: string | null;
  roleCodes: RoleCode[];
  isActive: boolean;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface UserQuery extends PageQuery {
  keyword?: string;
  role?: RoleCode;
  isActive?: boolean;
}

export interface CreateUserInput {
  name: string;
  email?: string | null;
  phone?: string | null;
  password: string;
  roleCodes: RoleCode[];
}

export interface UpdateUserInput {
  name?: string;
  email?: string | null;
  phone?: string | null;
  roleCodes?: RoleCode[];
  isActive?: boolean;
}
