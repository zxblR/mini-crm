<script setup lang="ts">
import { computed, onMounted, reactive } from 'vue';
import type { AiSuggestion, AiSuggestionResult, AiSuggestionType, LeadSummary } from '@mini-crm/shared';
import { RoleCode } from '@mini-crm/shared';

import { AI_TYPES, generateAiSuggestion, getAiSuggestion } from '@/api/ai';
import { normalizeApiError } from '@/api/http';
import { useAuthStore } from '@/stores/auth';
import AiSuggestionCard from './AiSuggestionCard.vue';

interface SuggestionState {
  suggestion: AiSuggestion<AiSuggestionResult> | null;
  loading: boolean;
  errorMessage: string | null;
  retryable: boolean;
  forbidden: boolean;
}

const props = defineProps<{ lead: LeadSummary }>();
const auth = useAuthStore();
const labels: Record<AiSuggestionType, string> = {
  INTENT_SCORE: '意向评分',
  FOLLOW_UP_SUMMARY: '跟进摘要',
  NEXT_ACTION: '下一步建议',
  SCRIPT: '话术生成',
  WAKE_UP: '沉默唤醒',
};
const states = reactive<Record<AiSuggestionType, SuggestionState>>({
  INTENT_SCORE: emptyState(),
  FOLLOW_UP_SUMMARY: emptyState(),
  NEXT_ACTION: emptyState(),
  SCRIPT: emptyState(),
  WAKE_UP: emptyState(),
});
const canGenerate = computed(() => {
  if (auth.hasAnyRole([RoleCode.Owner, RoleCode.Admin])) return true;
  return auth.hasRole(RoleCode.Sales) && props.lead.ownerId === auth.user?.id;
});

function emptyState(): SuggestionState {
  return { suggestion: null, loading: false, errorMessage: null, retryable: false, forbidden: false };
}

async function loadSaved(type: AiSuggestionType): Promise<void> {
  const state = states[type];
  state.loading = true;
  state.errorMessage = null;
  try {
    state.suggestion = await getAiSuggestion(props.lead.id, type);
  } catch (error) {
    const failure = normalizeApiError(error);
    if (failure.status === 404) state.suggestion = null;
    else if (failure.status === 403) state.forbidden = true;
    else state.errorMessage = failure.message;
  } finally {
    state.loading = false;
  }
}

async function generate(type: AiSuggestionType, forceRefresh: boolean): Promise<void> {
  const state = states[type];
  state.loading = true;
  state.errorMessage = null;
  state.retryable = false;
  try {
    state.suggestion = await generateAiSuggestion(props.lead.id, type, { forceRefresh });
  } catch (error) {
    const failure = normalizeApiError(error);
    if (failure.status === 403) {
      state.forbidden = true;
      state.errorMessage = '当前账号没有触发 AI 建议的权限';
    } else if (failure.status === 429 || failure.status === 503) {
      state.retryable = true;
      state.errorMessage = failure.status === 429 ? 'AI 请求过于频繁，请稍后重试' : 'AI 服务暂时不可用，可以稍后重试';
    } else {
      state.errorMessage = failure.message;
    }
  } finally {
    state.loading = false;
  }
}

onMounted(() => {
  void Promise.all(AI_TYPES.map((type) => loadSaved(type)));
});
</script>

<template>
  <el-card shadow="never">
    <template #header><div class="panel-header"><strong>AI 智能跟进</strong><span>建议仅供参考，不会自动修改线索或创建任务。</span></div></template>
    <div class="ai-grid">
      <AiSuggestionCard
        v-for="type in AI_TYPES"
        :key="type"
        :type="type"
        :label="labels[type]"
        :suggestion="states[type].suggestion"
        :loading="states[type].loading"
        :error-message="states[type].errorMessage"
        :retryable="states[type].retryable"
        :can-generate="canGenerate"
        :forbidden="states[type].forbidden"
        @generate="generate(type, $event)"
      />
    </div>
  </el-card>
</template>

<style scoped>
.panel-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.panel-header span { color: #64748b; font-size: 12px; }
.ai-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.ai-grid :deep(.ai-card:last-child) { grid-column: 1 / -1; }
@media (max-width: 800px) { .ai-grid { grid-template-columns: 1fr; } .ai-grid :deep(.ai-card:last-child) { grid-column: auto; } }
</style>
