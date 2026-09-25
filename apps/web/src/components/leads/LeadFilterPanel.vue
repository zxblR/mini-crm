<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import type { LeadQuery, UserSummary } from '@mini-crm/shared';
import { SortOrder } from '@mini-crm/shared';

import { LEAD_STATUS_LABELS, LEAD_STATUS_ORDER } from '@/constants/leads';

const props = defineProps<{
  modelValue: LeadQuery;
  owners: UserSummary[];
  showOwnerFilter: boolean;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: LeadQuery];
  search: [value: LeadQuery];
  reset: [];
}>();

const form = reactive<LeadQuery>({ ...props.modelValue });
const dateRange = ref<[Date, Date] | undefined>(
  props.modelValue.from && props.modelValue.to
    ? [new Date(props.modelValue.from), new Date(props.modelValue.to)]
    : undefined,
);

watch(
  () => props.modelValue,
  (value) => {
    Object.assign(form, value);
    dateRange.value = value.from && value.to ? [new Date(value.from), new Date(value.to)] : undefined;
  },
  { deep: true },
);

function submit(): void {
  form.from = dateRange.value?.[0].toISOString();
  form.to = dateRange.value?.[1].toISOString();
  const value = { ...form, page: 1 };
  emit('update:modelValue', value);
  emit('search', value);
}

function reset(): void {
  dateRange.value = undefined;
  Object.assign(form, {
    page: 1,
    pageSize: 20,
    keyword: undefined,
    status: undefined,
    ownerId: undefined,
    source: undefined,
    from: undefined,
    to: undefined,
    archived: undefined,
    sortBy: 'updatedAt',
    sortOrder: SortOrder.Desc,
  });
  emit('reset');
}
</script>

<template>
  <el-card class="filter-card" shadow="never">
    <el-form :model="form" inline @submit.prevent="submit">
      <el-form-item label="搜索">
        <el-input v-model="form.keyword" clearable placeholder="姓名、公司、手机号或邮箱" @keyup.enter="submit" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="form.status" clearable placeholder="全部状态">
          <el-option v-for="status in LEAD_STATUS_ORDER" :key="status" :label="LEAD_STATUS_LABELS[status]" :value="status" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="showOwnerFilter" label="负责人">
        <el-select v-model="form.ownerId" clearable filterable placeholder="全部负责人">
          <el-option v-for="owner in owners" :key="owner.id" :label="owner.name" :value="owner.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="来源">
        <el-input v-model="form.source" clearable placeholder="如 website" />
      </el-form-item>
      <el-form-item label="创建时间">
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
        />
      </el-form-item>
      <el-form-item label="归档">
        <el-select v-model="form.archived" clearable placeholder="全部">
          <el-option label="未归档" :value="false" />
          <el-option label="已归档" :value="true" />
        </el-select>
      </el-form-item>
      <el-form-item label="排序">
        <el-select v-model="form.sortBy">
          <el-option label="最近更新" value="updatedAt" />
          <el-option label="创建时间" value="createdAt" />
          <el-option label="下次跟进" value="nextFollowUpAt" />
          <el-option label="公司名称" value="company" />
        </el-select>
        <el-select v-model="form.sortOrder" class="sort-order">
          <el-option label="降序" :value="SortOrder.Desc" />
          <el-option label="升序" :value="SortOrder.Asc" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" native-type="submit">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<style scoped>
.filter-card { margin-bottom: 16px; }
.filter-card :deep(.el-form-item) { margin-bottom: 12px; }
.filter-card :deep(.el-input), .filter-card :deep(.el-select) { width: 190px; }
.filter-card :deep(.el-date-editor) { width: 360px; }
.filter-card .sort-order { width: 100px; margin-left: 8px; }
</style>
