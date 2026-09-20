import type { RoleCode } from '@mini-crm/shared';

export interface AuthenticatedUser {
  id: string;
  organizationId: string;
  name: string;
  email: string | null;
  phone: string | null;
  roles: RoleCode[];
}
