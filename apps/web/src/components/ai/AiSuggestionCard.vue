<script setup lang="ts">
import { computed } from 'vue';
import type {
  AiSuggestion,
  AiSuggestionResult,
  AiSuggestionType,
  FollowUpSummaryResult,
  IntentScoreResult,
  NextActionResult,
  ScriptResult,
  WakeUpResult,
} from '@mini-crm/shared';

import { formatDateTime } from '@/utils/format';

interface DisplayModel {
  headline: string;
  description: string;
  items: string[];
  meta: string | null;
}

function safeString(value: unknown, fallback = '—'): string {
  return typeof value === 'string' && value.length > 0 ? value : fallback;
}

function safeNumber(value: unknown, fallback = '—'): string {
  return typeof value === 'number' && Number.isFinite(value) ? String(value) : fallback;
}

function safeStringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
}

const props = defineProps<{
  type: AiSuggestionType;
  label: string;
  suggestion: AiSuggestion<AiSuggestionResult> | null;
  loading: boolean;
  errorMessage: string | null;
  retryable: boolean;
  canGenerate: boolean;
  forbidden: boolean;
}>();
const emit = defineEmits<{ generate: [forceRefresh: boolean] }>();

const display = computed<DisplayModel | null>(() => {
  if (!props.suggestion) return null;
  const result = props.suggestion.result;
  if (!result || typeof result !== 'object') return null;
  switch (props.type) {
    case 'INTENT_SCORE': {
      const value = result as IntentScoreResult;
      const reasons = Array.isArray(value.reasons) ? value.reasons : [];
      return {
        headline: safeNumber(value.score) + ' 分 · ' + safeString(value.level),
        description: '置信度 ' + (typeof value.confidence === 'number' ? Math.round(value.confidence * 100) : '—') + '%',
        items: safeStringArray(reasons),
        meta: null,
      };
    }
    case 'FOLLOW_UP_SUMMARY': {
      const value = result as FollowUpSummaryResult;
      const keyPoints = Array.isArray(value.keyPoints) ? value.keyPoints : [];
      const risks = Array.isArray(value.risks) ? value.risks : [];
      const openQuestions = Array.isArray(value.openQuestions) ? value.openQuestions : [];
      return {
        headline: safeString(value.summary),
        description: openQuestions.length ? '仍有 ' + openQuestions.length + ' 个待确认问题' : '暂无待确认问题',
        items: [...safeStringArray(keyPoints), ...safeStringArray(risks).map((item) => '风险：' + item)],
        meta: null,
      };
    }
    case 'NEXT_ACTION': {
      const value = result as NextActionResult;
      return {
        headline: safeString(value.action),
        description: safeString(value.rationale),
        items: [],
        meta: [safeString(value.priority, ''), typeof value.dueInHours === 'number' ? value.dueInHours + ' 小时内' : null]
          .filter((item): item is string => Boolean(item)).join(' · ') || null,
      };
    }
    case 'SCRIPT': {
      const value = result as ScriptResult;
      const personalizationNotes = Array.isArray(value.personalizationNotes) ? value.personalizationNotes : [];
      return {
        headline: safeString(value.subject),
        description: safeString(value.script),
        items: safeStringArray(personalizationNotes),
        meta: safeString(value.channel, '') || null,
      };
    }
    case 'WAKE_UP': {
      const value = result as WakeUpResult;
      return {
        headline: value.shouldWakeUp ? '建议唤醒客户' : '暂不建议唤醒',
        description: safeString(value.reason),
        items: [],
        meta: [safeString(value.suggestedChannel, ''), typeof value.suggestedAtHours === 'number' ? value.suggestedAtHours + ' 小时后' : null]
          .filter((item): item is string => Boolean(item)).join(' · ') || null,
      };
    }
  }
  return null;
});
</script>

<template>
  <el-card class="ai-card" :class="'ai-card--' + type.toLowerCase()" shadow="never">
    <template #header>
      <div class="ai-card__header">
        <div><strong>{{ label }}</strong><el-tag v-if="suggestion?.cached" size="small" type="info" effect="plain">缓存</el-tag></div>
        <div v-if="canGenerate && !forbidden">
          <el-button v-if="suggestion" link type="primary" :loading="loading" @click="emit('generate', true)">重新生成</el-button>
          <el-button v-else link type="primary" :loading="loading" @click="emit('generate', false)">生成</el-button>
        </div>
      </div>
    </template>
    <el-skeleton v-if="loading" :rows="3" animated />
    <el-alert v-else-if="errorMessage" :title="errorMessage" type="error" :closable="false">
      <template v-if="retryable && canGenerate" #default><el-button link type="danger" @click="emit('generate', false)">重试</el-button></template>
    </el-alert>
    <div v-else-if="display" class="ai-card__body">
      <h3>{{ display.headline }}</h3>
      <p>{{ display.description }}</p>
      <ul v-if="display.items.length"><li v-for="item in display.items" :key="item">{{ item }}</li></ul>
      <small v-if="display.meta">{{ display.meta }}</small>
      <small>模型：{{ suggestion?.model }} · 到期：{{ formatDateTime(suggestion?.expiresAt) }}</small>
    </div>
    <el-empty v-else :description="forbidden ? '当前角色只能查看已保存结果' : '尚未生成建议'" :image-size="58" />
  </el-card>
</template>

<style scoped>
.ai-card { border-left: 4px solid #94a3b8; }
.ai-card--intent_score { border-left-color: #7c3aed; }
.ai-card--follow_up_summary { border-left-color: #2563eb; }
.ai-card--next_action { border-left-color: #16a34a; }
.ai-card--script { border-left-color: #ea580c; }
.ai-card--wake_up { border-left-color: #0891b2; }
.ai-card__header, .ai-card__header > div { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.ai-card__body h3 { margin: 0 0 8px; font-size: 16px; }
.ai-card__body p { margin: 0 0 10px; color: #475569; white-space: pre-wrap; }
.ai-card__body ul { margin: 0 0 10px; padding-left: 20px; color: #475569; }
.ai-card__body small { display: block; margin-top: 6px; color: #94a3b8; }
</style>
