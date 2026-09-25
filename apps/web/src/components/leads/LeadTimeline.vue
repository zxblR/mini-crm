<script setup lang="ts">
import type { TimelineEvent } from '@mini-crm/shared';

import { formatDateTime } from '@/utils/format';

defineProps<{ events: TimelineEvent[] }>();
</script>

<template>
  <el-card shadow="never">
    <template #header><strong>客户时间线</strong></template>
    <el-empty v-if="events.length === 0" description="暂无时间线记录" />
    <el-timeline v-else>
      <el-timeline-item v-for="event in events" :key="event.id" :timestamp="formatDateTime(event.occurredAt)" placement="top">
        <strong>{{ event.title }}</strong>
        <p v-if="event.description">{{ event.description }}</p>
        <small>{{ event.actorName || '系统' }}</small>
      </el-timeline-item>
    </el-timeline>
  </el-card>
</template>

<style scoped>
p { margin: 6px 0; color: #475569; white-space: pre-wrap; }
small { color: #94a3b8; }
</style>
