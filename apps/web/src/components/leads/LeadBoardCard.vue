<script setup lang="ts">
import type { LeadSummary } from '@mini-crm/shared';

import LeadTagList from './LeadTagList.vue';
import { formatDateTime, leadDisplayName } from '@/utils/format';
import { usePermissions } from '@/composables/usePermissions';

defineProps<{ lead: LeadSummary }>();
const emit = defineEmits<{ status: [lead: LeadSummary] }>();
const permissions = usePermissions();
</script>

<template>
  <article class="board-card">
    <router-link class="board-card__title" :to="'/leads/' + lead.id">{{ leadDisplayName(lead.name, lead.company) }}</router-link>
    <p>{{ lead.company || lead.source }}</p>
    <LeadTagList :tags="lead.tags" />
    <div class="board-card__meta"><span>{{ lead.owner?.name ?? '未分配' }}</span><span>{{ formatDateTime(lead.nextFollowUpAt) }}</span></div>
    <el-button v-if="permissions.canChangeLeadStatus(lead)" class="board-card__action" text type="primary" @click="emit('status', lead)">切换状态</el-button>
  </article>
</template>

<style scoped>
.board-card { padding: 14px; background: #fff; border: 1px solid #e2e8f0; border-radius: 6px; }
.board-card__title { color: #1e293b; font-weight: 700; text-decoration: none; }
.board-card p { margin: 6px 0 10px; color: #64748b; font-size: 13px; }
.board-card__meta { display: grid; gap: 4px; margin-top: 12px; color: #94a3b8; font-size: 12px; }
.board-card__action { margin-top: 8px; padding-left: 0; }
</style>
