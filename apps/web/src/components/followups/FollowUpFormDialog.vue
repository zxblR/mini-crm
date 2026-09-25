<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import type { FollowUp } from '@mini-crm/shared';
import { FollowUpType, FOLLOW_UP_TYPE_LABELS } from '@mini-crm/shared';

import { createFollowUp, updateFollowUp } from '@/api/followups';
import { normalizeApiError } from '@/api/http';
import { usePermissions } from '@/composables/usePermissions';

interface FollowUpFormModel {
  type: FollowUpType;
  occurredAt: Date;
  summary: string;
  result: string;
  nextStepAt: Date | null;
}

const props = defineProps<{
  modelValue: boolean;
  leadId: string;
  followUp?: FollowUp | null;
}>();
const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  saved: [];
}>();
const permissions = usePermissions();
const formRef = ref<FormInstance>();
const submitting = ref(false);
const form = reactive<FollowUpFormModel>(emptyForm());
const title = computed(() => (props.followUp ? '编辑跟进记录' : '新增跟进记录'));

function emptyForm(): FollowUpFormModel {
  return { type: FollowUpType.Call, occurredAt: new Date(), summary: '', result: '', nextStepAt: null };
}

function resetForm(): void {
  const item = props.followUp;
  Object.assign(
    form,
    item
      ? {
          type: item.type,
          occurredAt: new Date(item.occurredAt),
          summary: item.summary,
          result: item.result ?? '',
          nextStepAt: item.nextStepAt ? new Date(item.nextStepAt) : null,
        }
      : emptyForm(),
  );
  void nextTick(() => formRef.value?.clearValidate());
}

const rules: FormRules<FollowUpFormModel> = {
  type: [{ required: true, message: '请选择跟进类型', trigger: 'change' }],
  occurredAt: [{ required: true, message: '请选择发生时间', trigger: 'change' }],
  summary: [
    { required: true, whitespace: true, message: '请输入跟进摘要', trigger: 'blur' },
    { max: 500, message: '摘要不能超过 500 个字符', trigger: 'blur' },
  ],
  result: [{ max: 1000, message: '结果不能超过 1000 个字符', trigger: 'blur' }],
};

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate();
  if (!valid) return;
  if (!permissions.isManager.value && form.occurredAt.getTime() > Date.now() + 24 * 60 * 60 * 1000) {
    ElMessage.warning('发生时间不能晚于当前时间 24 小时以上');
    return;
  }
  submitting.value = true;
  try {
    const input = {
      type: form.type,
      occurredAt: form.occurredAt.toISOString(),
      summary: form.summary.trim(),
      result: form.result.trim() || null,
      nextStepAt: form.nextStepAt?.toISOString() ?? null,
    };
    if (props.followUp) await updateFollowUp(props.followUp.id, input);
    else await createFollowUp(props.leadId, input);
    ElMessage.success(props.followUp ? '跟进记录已更新' : '跟进记录已新增');
    emit('saved');
    emit('update:modelValue', false);
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message);
  } finally {
    submitting.value = false;
  }
}

watch(() => props.modelValue, (open) => { if (open) resetForm(); });
</script>

<template>
  <el-dialog :model-value="modelValue" :title="title" width="620px" destroy-on-close @update:model-value="emit('update:modelValue', $event)">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="follow-up-grid">
        <el-form-item label="跟进类型" prop="type"><el-select v-model="form.type"><el-option v-for="type in FollowUpType" :key="type" :label="FOLLOW_UP_TYPE_LABELS[type]" :value="type" /></el-select></el-form-item>
        <el-form-item label="发生时间" prop="occurredAt"><el-date-picker v-model="form.occurredAt" type="datetime" /></el-form-item>
      </div>
      <el-form-item label="摘要" prop="summary"><el-input v-model="form.summary" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      <el-form-item label="结果" prop="result"><el-input v-model="form.result" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
      <el-form-item label="下一步时间"><el-date-picker v-model="form.nextStepAt" type="datetime" clearable /></el-form-item>
    </el-form>
    <template #footer><el-button @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="submitting" @click="submit">保存</el-button></template>
  </el-dialog>
</template>

<style scoped>
.follow-up-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; }
.follow-up-grid :deep(.el-select), .follow-up-grid :deep(.el-date-editor), :deep(.el-date-editor) { width: 100%; }
</style>
