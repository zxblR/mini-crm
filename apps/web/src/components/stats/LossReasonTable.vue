<script setup lang="ts">
import type { LossReasonStatsDTO } from '@mini-crm/shared';

defineProps<{ items: LossReasonStatsDTO[] }>();
</script>

<template>
  <el-table :data="items" size="small">
    <el-table-column label="流失原因" prop="lostReason" min-width="180" />
    <el-table-column label="数量" prop="count" width="100" />
  </el-table>
</template>
