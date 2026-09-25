<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Menu, UserFilled } from '@element-plus/icons-vue';
import { ROLE_LABELS } from '@mini-crm/shared';

import { useAuthStore } from '@/stores/auth';
import { useUiStore } from '@/stores/ui';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const ui = useUiStore();

const title = computed(() => route.meta.title ?? '工作台');
const userName = computed(() => auth.user?.name ?? '用户');
const roleLabel = computed(() => {
  const role = auth.user?.roles[0];
  return role ? ROLE_LABELS[role] : '成员';
});

async function handleLogout(): Promise<void> {
  try {
    await auth.logout();
  } catch {
    ElMessage.warning('退出请求未完成，本地会话已清除');
  } finally {
    await router.replace({ name: 'login' });
  }
}
</script>

<template>
  <header class="app-topbar">
    <div class="topbar-leading">
      <el-button
        class="mobile-menu-button"
        text
        aria-label="打开导航"
        @click="ui.openMobileSidebar"
      >
        <el-icon><Menu /></el-icon>
      </el-button>
      <el-button
        class="desktop-menu-button"
        text
        aria-label="折叠导航"
        @click="ui.toggleSidebar"
      >
        <el-icon><Menu /></el-icon>
      </el-button>
      <strong>{{ title }}</strong>
    </div>

    <el-dropdown trigger="click" @command="handleLogout">
      <button class="user-menu" type="button">
        <el-avatar :size="32"><el-icon><UserFilled /></el-icon></el-avatar>
        <span class="user-menu-copy">
          <strong>{{ userName }}</strong>
          <small>{{ roleLabel }}</small>
        </span>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="logout">退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </header>
</template>
