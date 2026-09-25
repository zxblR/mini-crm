<script setup lang="ts">
import { reactive, watch } from 'vue';
import type { UserQuery } from '@mini-crm/shared';
import { ROLE_LABELS, RoleCode } from '@mini-crm/shared';

const props = defineProps<{ modelValue: UserQuery }>();
const emit = defineEmits<{
  'update:modelValue': [value: UserQuery];
  search: [value: UserQuery];
}>();
const form = reactive<UserQuery>({ ...props.modelValue });
watch(() => props.modelValue, (value) => Object.assign(form, value), { deep: true });

function submit(): void {
  const value = { ...form, page: 1 };
  emit('update:modelValue', value);
  emit('search', value);
}

function reset(): void {
  Object.assign(form, { page: 1, pageSize: 20, keyword: undefined, role: undefined, isActive: undefined });
  submit();
}
</script>

<template>
  <el-card class="member-filter" shadow="never">
    <el-form inline @submit.prevent="submit">
      <el-form-item label="搜索"><el-input v-model="form.keyword" clearable placeholder="姓名、邮箱或手机" @keyup.enter="submit" /></el-form-item>
      <el-form-item label="角色"><el-select v-model="form.role" clearable placeholder="全部角色"><el-option v-for="role in RoleCode" :key="role" :label="ROLE_LABELS[role]" :value="role" /></el-select></el-form-item>
      <el-form-item label="状态"><el-select v-model="form.isActive" clearable placeholder="全部状态"><el-option label="启用" :value="true" /><el-option label="停用" :value="false" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" native-type="submit">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
    </el-form>
  </el-card>
</template>

<style scoped>
.member-filter { margin-bottom: 16px; }
.member-filter :deep(.el-form-item) { margin-bottom: 0; }
.member-filter :deep(.el-input), .member-filter :deep(.el-select) { width: 190px; }
</style>
