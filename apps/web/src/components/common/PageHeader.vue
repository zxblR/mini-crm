<script setup lang="ts">
defineProps<{
  title: string;
  description?: string;
}>();
</script>

<template>
  <header class="page-header">
    <div>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div class="page-header__actions"><slot name="actions" /></div>
  </header>
</template>

<style scoped>
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; margin-bottom: 20px; }
.page-header h1 { margin: 0; font-size: 24px; }
.page-header p { margin: 8px 0 0; color: #64748b; }
.page-header__actions { display: flex; gap: 12px; }
</style>
