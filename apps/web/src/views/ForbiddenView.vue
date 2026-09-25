<script setup lang="ts">
import { useRouter } from 'vue-router';

const router = useRouter();

function goToDashboard(): void {
  void router.push('/dashboard');
}

function goBack(): void {
  router.back();
}
</script>

<template>
  <section class="forbidden-view">
    <el-result icon="warning" title="403 无权限访问" sub-title="你的角色没有访问此页面的权限">
      <template #extra>
        <el-button type="primary" @click="goToDashboard">返回工作台</el-button>
        <el-button @click="goBack">返回上一页</el-button>
      </template>
    </el-result>
  </section>
</template>

<style scoped>
.forbidden-view {
  min-height: 420px;
  display: grid;
  place-items: center;
}
</style>
