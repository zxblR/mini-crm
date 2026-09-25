<script setup lang="ts">
import type { LeadSummary } from '@mini-crm/shared';
import { LeadStatus } from '@mini-crm/shared';

import LeadBoardCard from './LeadBoardCard.vue';
import { LEAD_STATUS_LABELS } from '@/constants/leads';

defineProps<{
  status: LeadStatus;
  items: LeadSummary[];
  total: number;
  loading: boolean;
  errorMessage: string | null;
}>();
const emit = defineEmits<{ status: [lead: LeadSummary]; loadMore: [] }>();
</script>

<template>
  <section class="board-column">
    <header><strong>{{ LEAD_STATUS_LABELS[status] }}</strong><el-tag size="small" effect="plain">{{ total }}</el-tag></header>
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" />
    <div v-loading="loading" class="board-column__body">
      <LeadBoardCard v-for="lead in items" :key="lead.id" :lead="lead" @status="emit('status', $event)" />
      <el-empty v-if="!loading && items.length === 0" description="暂无线索" :image-size="54" />
      <el-button v-if="items.length < total" class="full-width" @click="emit('loadMore')">加载更多</el-button>
    </div>
  </section>
</template>

<style scoped>
.board-column { flex: 0 0 300px; min-height: 480px; padding: 12px; background: #f1f5f9; border-radius: 8px; }
.board-column header { display: flex; align-items: center; justify-content: space-between; padding: 4px 2px 12px; }
.board-column__body { display: grid; gap: 10px; min-height: 180px; }
</style>
