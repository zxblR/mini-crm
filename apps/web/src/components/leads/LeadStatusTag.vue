<script setup lang="ts">
import { computed } from 'vue';
import { LeadStatus } from '@mini-crm/shared';

import { LEAD_STATUS_LABELS } from '@/constants/leads';

const props = defineProps<{ status: LeadStatus }>();
const tagType = computed<'primary' | 'success' | 'warning' | 'danger' | 'info'>(() => {
  if (props.status === LeadStatus.Won) return 'success';
  if (props.status === LeadStatus.Lost) return 'danger';
  if (props.status === LeadStatus.Negotiation) return 'warning';
  if (props.status === LeadStatus.New) return 'info';
  return 'primary';
});
</script>

<template><el-tag :type="tagType" effect="light">{{ LEAD_STATUS_LABELS[status] }}</el-tag></template>
