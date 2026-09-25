<script setup lang="ts">
const props = defineProps<{ page: number; pageSize: number; total: number }>();
const emit = defineEmits<{
  change: [page: number, pageSize: number];
}>();

function changePage(page: number): void {
  emit('change', page, props.pageSize);
}

function changePageSize(pageSize: number): void {
  emit('change', 1, pageSize);
}
</script>

<template>
  <div class="pagination-bar">
    <el-pagination
      background
      layout="total, sizes, prev, pager, next"
      :current-page="page"
      :page-size="pageSize"
      :page-sizes="[20, 50, 100]"
      :total="total"
      @current-change="changePage"
      @size-change="changePageSize"
    />
  </div>
</template>

<style scoped>
.pagination-bar { display: flex; justify-content: flex-end; padding-top: 20px; }
</style>
