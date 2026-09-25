<script setup lang="ts">
import { computed } from 'vue';
import { DataBoard } from '@element-plus/icons-vue';
import { useRoute } from 'vue-router';

const route = useRoute();
const title = computed(() => route.meta.title ?? '页面');
</script>

<template>
  <section class="placeholder-view">
    <div>
      <p class="eyebrow">PHASE 7</p>
      <h1>{{ title }}</h1>
      <p class="muted">当前为前端骨架占位，业务功能将在后续阶段接入。</p>
    </div>
    <el-empty description="暂无业务数据" :image-size="120">
      <template #image>
        <el-icon :size="64" color="#94a3b8"><DataBoard /></el-icon>
      </template>
    </el-empty>
  </section>
</template>
