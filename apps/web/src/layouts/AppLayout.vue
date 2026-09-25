<script setup lang="ts">
import { RouterView } from 'vue-router';

import AppBreadcrumb from '@/components/layout/AppBreadcrumb.vue';
import AppSidebar from '@/components/layout/AppSidebar.vue';
import AppTopbar from '@/components/layout/AppTopbar.vue';
</script>

<template>
  <div class="app-layout">
    <AppSidebar />
    <div class="app-main">
      <AppTopbar />
      <main class="app-content">
        <AppBreadcrumb />
        <RouterView />
      </main>
    </div>
  </div>
</template>
