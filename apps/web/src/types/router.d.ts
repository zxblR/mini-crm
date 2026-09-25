import type { RoleCode } from '@mini-crm/shared';

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean;
    roles?: RoleCode[];
    title?: string;
    menuKey?: string;
  }
}

export {};
