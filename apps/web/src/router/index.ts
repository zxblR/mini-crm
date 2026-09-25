import { createRouter, createWebHistory } from 'vue-router';
import { RoleCode } from '@mini-crm/shared';

import AppLayout from '@/layouts/AppLayout.vue';
import { useAuthStore } from '@/stores/auth';
import { pinia } from '@/stores/pinia';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: AppLayout,
      children: [
        {
          path: '',
          redirect: '/dashboard',
        },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/WorkspaceView.vue'),
          meta: { requiresAuth: true, title: '工作台', menuKey: 'dashboard' },
        },
        {
          path: 'leads',
          name: 'leads',
          component: () => import('@/components/common/RoutePlaceholder.vue'),
          meta: { requiresAuth: true, title: '线索', menuKey: 'leads' },
        },
        {
          path: 'tasks',
          name: 'tasks',
          component: () => import('@/components/common/RoutePlaceholder.vue'),
          meta: { requiresAuth: true, title: '任务', menuKey: 'tasks' },
        },
        {
          path: 'stats',
          name: 'stats',
          component: () => import('@/components/common/RoutePlaceholder.vue'),
          meta: { requiresAuth: true, title: '统计', menuKey: 'stats' },
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('@/components/common/RoutePlaceholder.vue'),
          meta: {
            requiresAuth: true,
            title: '设置',
            menuKey: 'settings',
            roles: [RoleCode.Owner, RoleCode.Admin],
          },
        },
        {
          path: '403',
          name: 'forbidden',
          component: () => import('@/components/common/RoutePlaceholder.vue'),
          meta: { requiresAuth: true, title: '无权限' },
        },
      ],
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
    },
    {
      path: '/workspace',
      redirect: '/dashboard',
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/dashboard',
    },
  ],
});

let hydrationPromise: Promise<void> | null = null;

router.beforeEach(async (to) => {
  const auth = useAuthStore(pinia);

  if (!auth.isHydrated) {
    hydrationPromise ??= auth.hydrate().finally(() => {
      hydrationPromise = null;
    });
    await hydrationPromise;
  }

  if (to.name === 'login' && auth.isAuthenticated) {
    return { name: 'dashboard' };
  }

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return {
      name: 'login',
      query: { redirect: to.fullPath },
    };
  }

  const requiredRoles = to.matched.flatMap((record) => record.meta.roles ?? []);
  if (requiredRoles.length > 0 && !auth.hasAnyRole(requiredRoles)) {
    return { name: 'forbidden' };
  }

  return true;
});

export default router;
