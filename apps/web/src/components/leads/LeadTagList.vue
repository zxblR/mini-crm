<script setup lang="ts">
import type { Tag } from '@mini-crm/shared';

defineProps<{ tags: Tag[] }>();
</script>

<template>
  <div v-if="tags.length" class="tag-list">
    <el-tag v-for="tag in tags" :key="tag.id" :color="tag.color" effect="plain">{{ tag.name }}</el-tag>
  </div>
  <span v-else class="muted-text">暂无标签</span>
</template>

<style scoped>
.tag-list { display: flex; flex-wrap: wrap; gap: 8px; }
.muted-text { color: #94a3b8; }
</style>
