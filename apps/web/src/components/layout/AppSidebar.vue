<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Close } from '@element-plus/icons-vue';

import { navigationItems } from '@/config/navigation';
import { useAuthStore } from '@/stores/auth';
import { useUiStore } from '@/stores/ui';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const ui = useUiStore();

const visibleItems = computed(() =>
  navigationItems.filter((item) => auth.hasAnyRole(item.roles)),
);
const isCollapsed = computed(() => ui.sidebarCollapsed && !ui.mobileSidebarOpen);

function navigate(path: string): void {
  ui.closeMobileSidebar();
  void router.push(path);
}
</script>

<template>
  <aside
    class="app-sidebar"
    :class="{
      'is-collapsed': ui.sidebarCollapsed,
      'is-mobile-open': ui.mobileSidebarOpen,
    }"
  >
    <div class="sidebar-brand">
      <span class="brand-mark">MINI CRM</span>
      <el-button
        class="sidebar-close"
        text
        aria-label="关闭导航"
        @click="ui.closeMobileSidebar"
      >
        <el-icon><Close /></el-icon>
      </el-button>
    </div>

    <el-menu
      class="sidebar-menu"
      :default-active="route.path"
      :collapse="isCollapsed"
      @select="navigate"
    >
      <el-menu-item v-for="item in visibleItems" :key="item.key" :index="item.path">
        <el-icon><component :is="item.icon" /></el-icon>
        <template #title>{{ item.label }}</template>
      </el-menu-item>
    </el-menu>
  </aside>

  <button
    v-if="ui.mobileSidebarOpen"
    class="sidebar-backdrop"
    aria-label="关闭导航"
    @click="ui.closeMobileSidebar"
  />
</template>
