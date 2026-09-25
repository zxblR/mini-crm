<script setup lang="ts">
import type { TaskItem } from '@mini-crm/shared';

import { formatDateTime } from '@/utils/format';

defineProps<{ items: TaskItem[]; total: number }>();
</script>

<template>
  <el-card shadow="never">
    <template #header><div class="panel-header"><strong>今日任务</strong><router-link to="/tasks">查看全部 {{ total }}</router-link></div></template>
    <el-empty v-if="items.length === 0" description="今天没有待处理任务" :image-size="72" />
    <div v-else class="panel-list">
      <article v-for="item in items" :key="item.id">
        <div><strong>{{ item.title }}</strong><span>{{ item.leadName }}</span></div>
        <el-tag :type="item.isOverdue ? 'danger' : 'warning'" effect="plain">
          {{ item.isOverdue ? '已逾期' : formatDateTime(item.dueAt) }}
        </el-tag>
      </article>
    </div>
  </el-card>
</template>

<style scoped>
.panel-header, article { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.panel-header a { color: #2563eb; font-size: 13px; text-decoration: none; }
.panel-list { display: grid; gap: 12px; }
article { padding-bottom: 12px; border-bottom: 1px solid #e2e8f0; }
article:last-child { padding-bottom: 0; border-bottom: 0; }
article div { display: grid; gap: 4px; }
article span { color: #64748b; font-size: 12px; }
</style>
