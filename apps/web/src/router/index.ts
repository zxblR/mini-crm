import { createRouter, createWebHistory } from 'vue-router';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/workspace',
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
    },
    {
      path: '/workspace',
      name: 'workspace',
      component: () => import('@/views/WorkspaceView.vue'),
    },
  ],
});

export default router;
