<script setup lang="ts">
import { reactive, watch } from 'vue';
import type { TaskQuery, UserSummary } from '@mini-crm/shared';
import { TaskStatus } from '@mini-crm/shared';

const props = defineProps<{
  modelValue: TaskQuery;
  owners: UserSummary[];
  showAssigneeFilter: boolean;
}>();
const emit = defineEmits<{
  'update:modelValue': [value: TaskQuery];
  search: [value: TaskQuery];
}>();

const form = reactive<TaskQuery>({ ...props.modelValue });
watch(() => props.modelValue, (value) => Object.assign(form, value), { deep: true });

function submit(): void {
  const value = { ...form, page: 1 };
  emit('update:modelValue', value);
  emit('search', value);
}

function reset(): void {
  Object.assign(form, { page: 1, pageSize: 20, status: undefined, assigneeId: undefined });
  submit();
}
</script>

<template>
  <el-card class="task-filter" shadow="never">
    <el-form inline @submit.prevent="submit">
      <el-form-item label="状态">
        <el-select v-model="form.status" clearable placeholder="全部状态">
          <el-option label="待处理" :value="TaskStatus.Pending" />
          <el-option label="已逾期" :value="TaskStatus.Overdue" />
          <el-option label="已完成" :value="TaskStatus.Completed" />
          <el-option label="已取消" :value="TaskStatus.Cancelled" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="showAssigneeFilter" label="负责人">
        <el-select v-model="form.assigneeId" clearable filterable placeholder="全部负责人">
          <el-option v-for="owner in owners" :key="owner.id" :label="owner.name" :value="owner.id" />
        </el-select>
      </el-form-item>
      <el-form-item><el-button type="primary" native-type="submit">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
    </el-form>
  </el-card>
</template>

<style scoped>
.task-filter { margin-bottom: 16px; }
.task-filter :deep(.el-form-item) { margin-bottom: 0; }
.task-filter :deep(.el-select) { width: 180px; }
</style>
