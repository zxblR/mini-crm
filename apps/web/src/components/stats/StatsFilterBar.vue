<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import type { UserSummary } from '@mini-crm/shared';
import { Granularity } from '@mini-crm/shared';

import type { StatsQuery } from '@/stores/stats';

const props = defineProps<{
  modelValue: StatsQuery;
  owners: UserSummary[];
  showOwnerFilter: boolean;
}>();
const emit = defineEmits<{
  'update:modelValue': [value: StatsQuery];
  search: [value: StatsQuery];
}>();

const form = reactive<StatsQuery>({ ...props.modelValue });
const dateRange = ref<[Date, Date]>([
  new Date(props.modelValue.from ?? Date.now() - 30 * 24 * 60 * 60 * 1000),
  new Date(props.modelValue.to ?? Date.now()),
]);

watch(
  () => props.modelValue,
  (value) => {
    Object.assign(form, value);
    if (value.from && value.to) dateRange.value = [new Date(value.from), new Date(value.to)];
  },
  { deep: true },
);

function submit(): void {
  const from = dateRange.value[0];
  const to = dateRange.value[1];
  if (from.getTime() >= to.getTime()) {
    ElMessage.warning('开始时间必须早于结束时间');
    return;
  }
  if (to.getTime() - from.getTime() > 366 * 24 * 60 * 60 * 1000) {
    ElMessage.warning('统计范围不能超过 366 天');
    return;
  }
  const value: StatsQuery = {
    ...form,
    from: from.toISOString(),
    to: to.toISOString(),
  };
  emit('update:modelValue', value);
  emit('search', value);
}
</script>

<template>
  <el-card class="stats-filter" shadow="never">
    <el-form inline @submit.prevent="submit">
      <el-form-item label="统计范围">
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          :clearable="false"
        />
      </el-form-item>
      <el-form-item label="趋势粒度">
        <el-select v-model="form.granularity">
          <el-option label="按日" :value="Granularity.Day" />
          <el-option label="按周" :value="Granularity.Week" />
          <el-option label="按月" :value="Granularity.Month" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="showOwnerFilter" label="负责人">
        <el-select v-model="form.ownerId" clearable filterable placeholder="全部负责人">
          <el-option v-for="owner in owners" :key="owner.id" :label="owner.name" :value="owner.id" />
        </el-select>
      </el-form-item>
      <el-form-item><el-button type="primary" native-type="submit">应用筛选</el-button></el-form-item>
    </el-form>
  </el-card>
</template>

<style scoped>
.stats-filter { margin-bottom: 20px; }
.stats-filter :deep(.el-form-item) { margin-bottom: 0; }
.stats-filter :deep(.el-date-editor) { width: 360px; }
.stats-filter :deep(.el-select) { width: 150px; }
</style>
