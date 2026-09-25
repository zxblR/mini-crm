<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { TaskItem } from '@mini-crm/shared';

import { normalizeApiError } from '@/api/http';
import { useTaskStore } from '@/stores/tasks';

const props = defineProps<{
  modelValue: boolean;
  task: TaskItem | null;
  action: 'complete' | 'cancel';
}>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; resolved: [] }>();
const store = useTaskStore();
const note = ref('');
const submitting = ref(false);
const title = computed(() => props.action === 'complete' ? '确认完成任务' : '确认取消任务');

watch(() => props.modelValue, (open) => { if (open) note.value = ''; });

async function submit(): Promise<void> {
  if (!props.task) return;
  submitting.value = true;
  try {
    await store.resolve(props.task.id, props.action, { note: note.value.trim() || null });
    ElMessage.success(props.action === 'complete' ? '任务已完成' : '任务已取消');
    emit('resolved');
    emit('update:modelValue', false);
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message);
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="title" width="480px" @update:model-value="emit('update:modelValue', $event)">
    <el-alert :title="action === 'complete' ? '完成后任务不可再次编辑。' : '取消后任务不可再次编辑。'" type="warning" :closable="false" />
    <el-form label-position="top" class="resolve-form"><el-form-item label="处理说明"><el-input v-model="note" type="textarea" :rows="3" /></el-form-item></el-form>
    <template #footer><el-button @click="emit('update:modelValue', false)">返回</el-button><el-button :type="action === 'complete' ? 'success' : 'danger'" :loading="submitting" @click="submit">确认</el-button></template>
  </el-dialog>
</template>

<style scoped>.resolve-form { margin-top: 18px; }</style>
