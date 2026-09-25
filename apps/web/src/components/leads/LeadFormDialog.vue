<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import type {
  CreateLeadRequest,
  LeadDetail,
  LeadSummary,
  OpenLeadStatus,
  UpdateLeadRequest,
} from '@mini-crm/shared';
import { LeadStatus } from '@mini-crm/shared';

import { normalizeApiError } from '@/api/http';
import { usePermissions } from '@/composables/usePermissions';
import { LEAD_STATUS_LABELS, OPEN_LEAD_STATUSES } from '@/constants/leads';
import { useDirectoryStore } from '@/stores/directory';
import { useLeadStore } from '@/stores/leads';

interface LeadFormModel {
  name: string;
  company: string;
  phone: string;
  email: string;
  source: string;
  industry: string;
  region: string;
  notes: string;
  status: OpenLeadStatus;
  ownerId: string | undefined;
  tagIds: string[];
  nextFollowUpAt: Date | undefined;
}

const props = defineProps<{
  modelValue: boolean;
  lead?: LeadSummary | LeadDetail | null;
}>();
const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  saved: [lead: LeadSummary];
}>();

const store = useLeadStore();
const directory = useDirectoryStore();
const permissions = usePermissions();
const formRef = ref<FormInstance>();
const submitting = ref(false);
const form = reactive<LeadFormModel>(emptyForm());
const isEdit = computed(() => Boolean(props.lead));
const title = computed(() => (isEdit.value ? '编辑线索' : '新建线索'));

function emptyForm(): LeadFormModel {
  return {
    name: '',
    company: '',
    phone: '',
    email: '',
    source: '',
    industry: '',
    region: '',
    notes: '',
    status: LeadStatus.New,
    ownerId: undefined,
    tagIds: [],
    nextFollowUpAt: undefined,
  };
}

function resetForm(): void {
  const lead = props.lead;
  Object.assign(
    form,
    lead
      ? {
          name: lead.name ?? '',
          company: lead.company ?? '',
          phone: lead.phone ?? '',
          email: lead.email ?? '',
          source: lead.source,
          industry: lead.industry ?? '',
          region: lead.region ?? '',
          notes: 'notes' in lead ? lead.notes ?? '' : '',
          status: LeadStatus.New,
          ownerId: lead.ownerId ?? undefined,
          tagIds: lead.tags.map((tag) => tag.id),
          nextFollowUpAt: lead.nextFollowUpAt ? new Date(lead.nextFollowUpAt) : undefined,
        }
      : emptyForm(),
  );
  void nextTick(() => formRef.value?.clearValidate());
}

function requireIdentity(
  _rule: unknown,
  _value: string,
  callback: (error?: Error) => void,
): void {
  if (form.name.trim() || form.company.trim()) callback();
  else callback(new Error('姓名和公司至少填写一项'));
}

const rules: FormRules<LeadFormModel> = {
  name: [
    { max: 100, message: '姓名不能超过 100 个字符', trigger: 'blur' },
    { validator: requireIdentity, trigger: 'blur' },
  ],
  company: [
    { max: 160, message: '公司不能超过 160 个字符', trigger: 'blur' },
    { validator: requireIdentity, trigger: 'blur' },
  ],
  phone: [{ max: 32, message: '手机号不能超过 32 个字符', trigger: 'blur' }],
  email: [
    { type: 'email', message: '请输入有效邮箱', trigger: 'blur' },
    { max: 254, message: '邮箱不能超过 254 个字符', trigger: 'blur' },
  ],
  source: [
    { required: true, whitespace: true, message: '请输入线索来源', trigger: 'blur' },
    { max: 60, message: '来源不能超过 60 个字符', trigger: 'blur' },
  ],
  industry: [{ max: 80, message: '行业不能超过 80 个字符', trigger: 'blur' }],
  region: [{ max: 100, message: '地区不能超过 100 个字符', trigger: 'blur' }],
};

function nullable(value: string): string | null {
  const trimmed = value.trim();
  return trimmed || null;
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate();
  if (!valid) return;
  submitting.value = true;
  try {
    const common: UpdateLeadRequest = {
      name: nullable(form.name),
      company: nullable(form.company),
      phone: nullable(form.phone),
      email: nullable(form.email),
      source: form.source.trim(),
      industry: nullable(form.industry),
      region: nullable(form.region),
      tagIds: [...new Set(form.tagIds)],
      nextFollowUpAt: form.nextFollowUpAt?.toISOString() ?? null,
    };
    if (!isEdit.value || (props.lead && 'notes' in props.lead) || form.notes.trim()) {
      common.notes = nullable(form.notes);
    }
    if (permissions.isManager.value) common.ownerId = form.ownerId ?? null;

    const input: CreateLeadRequest | UpdateLeadRequest = isEdit.value
      ? common
      : { ...common, source: form.source.trim(), status: form.status };
    const saved = await store.saveLead(input, props.lead?.id);
    ElMessage.success(isEdit.value ? '线索已更新' : '线索已创建');
    emit('saved', saved);
    emit('update:modelValue', false);
  } catch (error) {
    const failure = normalizeApiError(error);
    if (failure.code === 'LEAD_DUPLICATE') {
      ElMessage.error('手机号或邮箱与现有线索重复，请检查后重试');
    } else {
      ElMessage.error(failure.message);
    }
  } finally {
    submitting.value = false;
  }
}

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return;
    resetForm();
    await directory.loadLeadDirectories().catch(() => undefined);
  },
);
</script>

<template>
  <el-dialog :model-value="modelValue" :title="title" width="720px" destroy-on-close @update:model-value="emit('update:modelValue', $event)">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="form-grid">
        <el-form-item label="姓名" prop="name"><el-input v-model="form.name" maxlength="100" /></el-form-item>
        <el-form-item label="公司" prop="company"><el-input v-model="form.company" maxlength="160" /></el-form-item>
        <el-form-item label="手机" prop="phone"><el-input v-model="form.phone" maxlength="32" /></el-form-item>
        <el-form-item label="邮箱" prop="email"><el-input v-model="form.email" maxlength="254" /></el-form-item>
        <el-form-item label="来源" prop="source"><el-input v-model="form.source" maxlength="60" placeholder="如 website" /></el-form-item>
        <el-form-item v-if="!isEdit" label="初始状态"><el-select v-model="form.status"><el-option v-for="status in OPEN_LEAD_STATUSES" :key="status" :label="LEAD_STATUS_LABELS[status]" :value="status" /></el-select></el-form-item>
        <el-form-item label="行业" prop="industry"><el-input v-model="form.industry" maxlength="80" /></el-form-item>
        <el-form-item label="地区" prop="region"><el-input v-model="form.region" maxlength="100" /></el-form-item>
        <el-form-item v-if="permissions.isManager.value" label="负责人"><el-select v-model="form.ownerId" clearable filterable><el-option v-for="owner in directory.owners" :key="owner.id" :label="owner.name" :value="owner.id" /></el-select></el-form-item>
        <el-form-item label="下次跟进"><el-date-picker v-model="form.nextFollowUpAt" type="datetime" placeholder="选择时间" /></el-form-item>
      </div>
      <el-form-item label="标签"><el-select v-model="form.tagIds" multiple filterable><el-option v-for="tag in directory.tags" :key="tag.id" :label="tag.name" :value="tag.id" /></el-select></el-form-item>
      <el-form-item label="备注"><el-input v-model="form.notes" type="textarea" :rows="4" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="submitting" @click="submit">保存</el-button></template>
  </el-dialog>
</template>

<style scoped>
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 20px; }
.form-grid :deep(.el-select), .form-grid :deep(.el-date-editor), :deep(.el-select) { width: 100%; }
@media (max-width: 720px) { .form-grid { grid-template-columns: 1fr; } }
</style>
