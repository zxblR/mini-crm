<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import type { LeadSummary } from '@mini-crm/shared';
import { LeadStatus } from '@mini-crm/shared';

import { normalizeApiError } from '@/api/http';
import { usePermissions } from '@/composables/usePermissions';
import {
  LEAD_STATUS_LABELS,
  LEAD_STATUS_ORDER,
  NEXT_SALES_STATUS,
  OPEN_LEAD_STATUSES,
} from '@/constants/leads';
import { useLeadStore } from '@/stores/leads';

interface StatusForm {
  status: LeadStatus | null;
  note: string;
  outcomeNote: string;
  lostReason: string;
}

const props = defineProps<{ modelValue: boolean; lead: LeadSummary | null }>();
const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  changed: [];
}>();
const store = useLeadStore();
const permissions = usePermissions();
const formRef = ref<FormInstance>();
const submitting = ref(false);
const form = reactive<StatusForm>({ status: null, note: '', outcomeNote: '', lostReason: '' });

const options = computed(() => {
  if (!props.lead) return [];
  if (permissions.isManager.value) {
    if (props.lead.status === LeadStatus.Won || props.lead.status === LeadStatus.Lost) {
      return OPEN_LEAD_STATUSES;
    }
    return LEAD_STATUS_ORDER.filter((status) => status !== props.lead?.status);
  }
  return NEXT_SALES_STATUS[props.lead.status] ?? [];
});
const terminal = computed(() => form.status === LeadStatus.Won || form.status === LeadStatus.Lost);

const rules: FormRules<StatusForm> = {
  status: [{ required: true, message: '请选择目标状态', trigger: 'change' }],
  note: [{ max: 500, message: '备注不能超过 500 个字符', trigger: 'blur' }],
  outcomeNote: [{ max: 500, message: '结果说明不能超过 500 个字符', trigger: 'blur' }],
  lostReason: [{ max: 200, message: '流失原因不能超过 200 个字符', trigger: 'blur' }],
};

async function submit(): Promise<void> {
  if (!props.lead) return;
  const valid = await formRef.value?.validate();
  if (!valid || !form.status) return;
  if (terminal.value && !form.outcomeNote.trim()) {
    ElMessage.warning('进入赢单或输单时必须填写结果说明');
    return;
  }
  if (form.status === LeadStatus.Lost && !form.lostReason.trim()) {
    ElMessage.warning('进入输单时必须填写流失原因');
    return;
  }

  submitting.value = true;
  try {
    await store.setStatus(props.lead, {
      status: form.status,
      note: form.note.trim() || null,
      outcomeNote: form.outcomeNote.trim() || null,
      lostReason: form.lostReason.trim() || null,
    });
    ElMessage.success('线索状态已更新');
    emit('changed');
    emit('update:modelValue', false);
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message);
  } finally {
    submitting.value = false;
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) Object.assign(form, { status: null, note: '', outcomeNote: '', lostReason: '' });
  },
);
</script>

<template>
  <el-dialog :model-value="modelValue" title="切换线索状态" width="520px" @update:model-value="emit('update:modelValue', $event)">
    <el-alert v-if="options.length === 0" type="warning" :closable="false" title="当前角色没有可用的状态迁移" />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="目标状态" prop="status"><el-select v-model="form.status" class="full-width"><el-option v-for="status in options" :key="status" :label="LEAD_STATUS_LABELS[status]" :value="status" /></el-select></el-form-item>
      <el-form-item label="备注" prop="note"><el-input v-model="form.note" type="textarea" maxlength="500" show-word-limit /></el-form-item>
      <el-form-item v-if="terminal" label="结果说明" prop="outcomeNote"><el-input v-model="form.outcomeNote" type="textarea" maxlength="500" show-word-limit /></el-form-item>
      <el-form-item v-if="form.status === LeadStatus.Lost" label="流失原因" prop="lostReason"><el-input v-model="form.lostReason" maxlength="200" show-word-limit /></el-form-item>
    </el-form>
    <template #footer><el-button @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" :disabled="options.length === 0" :loading="submitting" @click="submit">确认切换</el-button></template>
  </el-dialog>
</template>
