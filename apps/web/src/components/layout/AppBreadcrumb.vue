<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink, useRoute } from 'vue-router';

const route = useRoute();
const items = computed(() =>
  route.matched
    .filter((record) => record.meta.title)
    .map((record) => ({
      title: record.meta.title as string,
      path: record.path,
    })),
);
</script>

<template>
  <el-breadcrumb v-if="items.length" class="app-breadcrumb">
    <el-breadcrumb-item v-for="(item, index) in items" :key="item.path">
      <RouterLink v-if="index < items.length - 1" :to="item.path">{{ item.title }}</RouterLink>
      <span v-else>{{ item.title }}</span>
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>
