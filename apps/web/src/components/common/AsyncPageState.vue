<script setup lang="ts">
defineProps<{
  loading?: boolean;
  errorMessage?: string | null;
  empty?: boolean;
  emptyDescription?: string;
}>();

defineEmits<{ retry: [] }>();
</script>

<template>
  <el-skeleton v-if="loading" :rows="6" animated />
  <el-result v-else-if="errorMessage" icon="error" title="加载失败" :sub-title="errorMessage">
    <template #extra><el-button type="primary" @click="$emit('retry')">重新加载</el-button></template>
  </el-result>
  <el-empty v-else-if="empty" :description="emptyDescription ?? '暂无数据'" />
  <slot v-else />
</template>
