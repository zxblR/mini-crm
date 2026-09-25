<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import type { TaskItem, UserSummary } from '@mini-crm/shared';

import { normalizeApiError } from '@/api/http';
import { usePermissions } from '@/composables/usePermissions';
import { useTaskStore } from '@/stores/tasks';

interface TaskForm { title: string; dueAt: Date; assigneeId: string }

const props = defineProps<{
  modelValue: boolean;
  task: TaskItem | null;
  owners: UserSummary[];
}>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [] }>();
const store = useTaskStore();
const permissions = usePermissions();
const formRef = ref<FormInstance>();
const submitting = ref(false);
const form = reactive<TaskForm>({ title: '', dueAt: new Date(), assigneeId: '' });
const rules: FormRules<TaskForm> = {
  title: [{ max: 200, message: '标题不能超过 200 个字符', trigger: 'blur' }],
  dueAt: [{ required: true, message: '请选择截止时间', trigger: 'change' }],
};

watch(() => props.modelValue, (open) => {
  if (open && props.task) Object.assign(form, { title: props.task.title, dueAt: new Date(props.task.dueAt), assigneeId: props.task.assignee.id });
});

async function submit(): Promise<void> {
  if (!props.task || !(await formRef.value?.validate())) return;
  submitting.value = true;
  try {
    await store.edit(props.task.id, {
      title: form.title.trim(),
      dueAt: form.dueAt.toISOString(),
      assigneeId: permissions.isManager.value ? form.assigneeId : undefined,
    });
    ElMessage.success('任务已更新');
    emit('saved');
    emit('update:modelValue', false);
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message);
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="编辑任务" width="520px" @update:model-value="emit('update:modelValue', $event)">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="标题" prop="title"><el-input v-model="form.title" maxlength="200" /></el-form-item>
      <el-form-item label="截止时间" prop="dueAt"><el-date-picker v-model="form.dueAt" type="datetime" class="full-width" /></el-form-item>
      <el-form-item v-if="permissions.isManager.value" label="负责人"><el-select v-model="form.assigneeId" filterable class="full-width"><el-option v-for="owner in owners" :key="owner.id" :label="owner.name" :value="owner.id" /></el-select></el-form-item>
    </el-form>
    <template #footer><el-button @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="submitting" @click="submit">保存</el-button></template>
  </el-dialog>
</template>
